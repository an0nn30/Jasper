# Bundled plugin APIs

Five bundled plugins export a package of Java types that other plugins compile against and call: Credential Vault
hands credentials to Remote and File Server, the Editor opens the files Remote and Project Index find, Remote lists
projects on saved hosts for Project Index, Snippets saves commands for Shell History, and Enterprise tells plugins
that its look is active. A plugin of yours uses these APIs, and exports its own, through the same mechanism. Every
type below is in the [SDK reference](sdk-reference.html); [writing a plugin](plugin-authoring.md) covers the rest of
the SDK.

| Package | Provider | Version | Service | Published with | Bundled consumers |
| --- | --- | --- | --- | --- | --- |
| [`dev.jasper.vault.api`](sdk-reference.html#package-dev.jasper.vault.api) | Credential Vault, `dev.jasper.vault` | 0.6.0 | [`VaultApi`](sdk-reference.html#dev.jasper.vault.api.VaultApi) | `publishPerConsumer`: each consumer gets its own instance | Remote, File Server |
| [`dev.jasper.editor.api`](sdk-reference.html#package-dev.jasper.editor.api) | Editor, `dev.jasper.editor` | 0.10.0 | [`Editor`](sdk-reference.html#dev.jasper.editor.api.Editor) | `publish` | Remote, Project Index |
| [`dev.jasper.remote.api`](sdk-reference.html#package-dev.jasper.remote.api) | Remote, `dev.jasper.remote` | 0.14.0 | [`RemoteProjects`](sdk-reference.html#dev.jasper.remote.api.RemoteProjects) | `publish` | Project Index |
| [`dev.jasper.snippets.api`](sdk-reference.html#package-dev.jasper.snippets.api) | Snippets, `dev.jasper.snippets` | 0.3.0 | [`SnippetService`](sdk-reference.html#dev.jasper.snippets.api.SnippetService) | `publish` | Shell History |
| [`dev.jasper.enterprise.api`](sdk-reference.html#package-dev.jasper.enterprise.api) | Enterprise, `dev.jasper.enterprise` | 0.2.0 | [`Workstation`](sdk-reference.html#dev.jasper.enterprise.api.Workstation) | `publish`, while the Enterprise look is active | Credential Vault, Remote, File Server |

Every bundled consumer declares its provider as an optional requirement and runs without it, except Shell History,
which fails to start when Snippets is not loaded ([Snippets API](#snippets-api)).

## How exports work

A provider names the packages it exports in `plugin.toml`:

```toml
exports = ["dev.jasper.vault.api"]
```

- Each entry is a package name: segments of `[a-z_][a-z0-9_]*` separated by dots. A reserved package
  (`dev.jasper.sdk`, `dev.jasper.app`, `dev.jasper.terminal`, `dev.jasper.buddy`, `java`, `javax`, `jdk`, or a
  subpackage of one) is refused. Either mistake stops the plugin loading: "exports is not a package name: …",
  "exports names a reserved package: …".
- An export is one exact package. `dev.example.tool.api` does not export `dev.example.tool.api.model`.

**Class loading.** Each plugin has its own class loader. A consumer's loader imports the exported packages of every
plugin it names under `requires`, hard or optional, that loaded. A class in an imported package is loaded by the
provider's loader, so consumer and provider share one `VaultApi` class and the instance the consumer receives is of
that type.

- Nothing else of the provider is visible: not its other packages and not the libraries it bundles. Only classes are
  shared; the consumer's loader does not find resources in the exported package.
- A provider that did not load (not installed, disabled, refused, or skipped when Jasper resolved the plugins) exports
  nothing, and its API classes do not exist for the consumer ([when the provider is absent](#when-the-provider-is-absent)).
- A plugin may not contain a class in a package that a loaded dependency exports. Jasper refuses it: "*jar* defines a
  class in *package*, which a dependency exports". Compile against an API; never bundle it.

**Services.** A provider publishes an interface from one of its exported packages during its `start`
([exporting your own API](#exporting-your-own-api)).

- Plugins start dependencies first, through hard and optional requirements alike, and otherwise in id order, so a
  provider's services exist before its consumers start. A cycle through any requirements skips every plugin in it
  ("dependency cycle").
- Just before each consumer starts, Jasper prepares its instances: it calls the factory of every service the
  consumer's providers published, once, on the UI thread, with the consumer's
  [`PluginInfo`](sdk-reference.html#dev.jasper.sdk.PluginInfo). A factory that throws is reported against the
  provider; it, and one that returns null, gives that consumer no instance.
- Plugins load once per launch: installing, enabling or disabling a provider takes effect at the next start. A service
  a consumer finds at start stays for the session; one it does not find does not appear later.

A plugin's **Details** in Settings › Plugins lists its **Requires** (for example `dev.jasper.vault >=0.2.0 (optional)`)
and its **Exports** ([Details](plugins.md#details)).

In this repository, `verifyPluginArchitecture` lets a plugin reference only the JDK, the SDK (not the testkit), its
own packages, the libraries it bundles and the exported packages listed for it in `declaredImports`
(`gradle/plugin-architecture.gradle.kts`), and lets Credential Vault, Remote and File Server name
`dev.jasper.enterprise.api` only in their own `workstation` package.

## Using an exported API

### Declaring the provider

```toml
requires = [{ id = "dev.jasper.vault", version = ">=0.6", optional = true }]
```

- **Optional** (`optional = true`): the plugin starts whether or not the provider is there. Jasper does not check the
  version range of an optional requirement: whatever version loaded serves the plugin.
- **Hard** (`optional` absent or `false`): the plugin is skipped unless the provider is present, in range and running:
  "requires dev.jasper.vault, which is not available", "requires dev.jasper.vault >=0.6.0; found 0.5.0",
  "requires dev.jasper.vault, which failed to start".

[`requires`](plugin-authoring.md#requires) has the full rules.

### Compiling against it

In this repository a consumer compiles against the provider's module and tests with it:

```kotlin
dependencies {
    compileOnly(project(":jasper-sdk"))
    compileOnly(project(":jasper-plugin-vault"))
    testImplementation(project(":jasper-sdk"))
    testImplementation(project(":jasper-sdk-testkit"))
    testImplementation(project(":jasper-plugin-vault"))
}
```

and its entry in `declaredImports` lists `dev.jasper.vault.api`.

Outside the repository there is no separate API jar: the SDK release jars are `jasper-sdk` and `jasper-sdk-testkit`
only. Compile against the provider's own jar, `jasper-plugin-<name>.jar` (for the Vault, `jasper-plugin-vault.jar`), from the
bundled plugins folder of a Jasper installation, `plugins/<id>/` beside the application jar:

| Installation | Bundled plugin folder |
| --- | --- |
| macOS | `Jasper.app/Contents/app/plugins/<id>/` |
| Windows | `app\plugins\<id>\` in the installation folder |
| Linux package | `lib/app/plugins/<id>/` in the installation folder |
| Portable archive | `lib/plugins/<id>/` |

Copied into the [external example](examples/external-plugin/README.md)'s `libs/`, it joins the build like the SDK:

```kotlin
dependencies {
    compileOnly(files("libs/jasper-plugin-vault.jar"))
    testImplementation(files("libs/jasper-plugin-vault.jar"))
}
```

Keep it `compileOnly`. As an `implementation` dependency it would be packed into the plugin, and Jasper refuses a
plugin that carries a dependency's exported package. The jar holds the provider's other packages too, and your build
does not stop you naming them; reference only the exported package, since anything else compiles but throws
`NoClassDefFoundError` in Jasper.

### Looking the service up

Look services up in `start`:

```java
private Optional<VaultApi> vault = Optional.empty();

@Override public void start(PluginContext context) {
    try { vault = context.services().find(VaultApi.class); }
    catch (NoClassDefFoundError absent) { vault = Optional.empty(); }
}
```

- [`find(api)`](sdk-reference.html#dev.jasper.sdk.services.Services) returns this plugin's instance, or empty.
  `require(api)` returns it or throws
  [`ServiceUnavailableException`](sdk-reference.html#dev.jasper.sdk.services.ServiceUnavailableException) ("No service
  is available for …").
- Both look at every provider the plugin requires, hard or optional, run no provider code, are safe from any thread
  and return the same instance all session.
- With a hard requirement, `require` in `start` is enough: the plugin does not start without the provider, and if the
  provider's factory failed, `require` throws and the plugin fails to start.

### When the provider is absent

When an optional provider did not load, its API classes cannot load either, and the first **executed** reference to
one throws `NoClassDefFoundError`: a class literal such as `VaultApi.class`, a call, a cast, a static field such as
`VaultApi.LOCK_STATE_CHANGED`, or creating a lambda or method reference whose parameter is an API type. Fields, method
signatures and code that never runs may name API types, with two exceptions: the JVM needs the API class as soon as it
loads a class that catches or throws an API exception class (`TooLargeException`, `NotConnectedException`), or that
passes or returns a type of its own where an API type is expected (a `StoreDocument` passed to `Editor.open` or
returned from a `reopener` resolver). Testing a failure with `instanceof NotConnectedException`, as Project Index's
`RemoteBridge` does, is safe.

- Catch the error around the lookup, as above.
- After that, run code that names the API only once the service was found. Test `isPresent()` first:
  `vault.ifPresent(api -> …)` throws when the Vault is absent, because creating the lambda loads `VaultApi`.
- Put code covered by the two exceptions in a class the plugin creates only once the service was found, as Remote
  does with `RemoteEditing`, which builds Remote's `EditorDocument`s and passes them to the Editor.
- Project Index keeps every reference to Remote's API in one class, `RemoteBridge`, whose `find` catches the error;
  Credential Vault, Remote and File Server keep their Enterprise lookup in a `Layout` enum the same way.
- Say what is missing where the feature would be. Without the Editor, Remote reports "Could not open a remote file" /
  "Install the Editor plugin to open *path*.", and Project Index disables its file rows with "Install the Editor plugin
  to open files".

A provider that loaded but failed to start, was skipped because a plugin it requires did not start, or whose factory
failed, leaves its classes visible and the lookup empty.

### Methods newer than the provider

Because an optional requirement's range is not checked, an older provider than you declared may serve your plugin:
calling a method its API lacks throws `NoSuchMethodError`, and using a type it lacks throws `NoClassDefFoundError`.
Guard such a call, catching `NoClassDefFoundError` too when it names a newer type:

```java
try { reopener = editor.reopener("dev.example.tool:", this::reopen); }
catch (NoSuchMethodError older) { /* this Editor keeps no recent files */ }
```

Remote guards `reopener` and `locationDocuments` this way.

### Testing a consumer

With the testkit's [`FakePluginHost`](sdk-reference.html#dev.jasper.sdk.testing.FakePluginHost), start a stand-in
provider whose `start` publishes a fake, then start your plugin with the provider's id among its optional (or hard)
requirements:

```java
try (var host = new FakePluginHost()) {
    host.start(new PluginInfo("dev.jasper.editor", "Editor", "0.10.0", Set.of()), Set.of(), Set.of(),
        context -> context.services().publish(Editor.class, fakeEditor));
    host.start(TOOL, Set.of(), Set.of("dev.jasper.editor"), new ToolPlugin());
    // drive the plugin, then assert on what fakeEditor received
}
```

The fake host does not check exported packages or class visibility: an absent provider's API is still on the test
classpath, so the `NoClassDefFoundError` path cannot be exercised there
([differences from Jasper](../jasper-sdk-testkit/README.md#differences-from-jasper)).

## Credential Vault API

Package [`dev.jasper.vault.api`](sdk-reference.html#package-dev.jasper.vault.api), from Credential Vault
(`dev.jasper.vault`, 0.6.0). The user's side is in [Credential Vault](credential-vault.md#other-plugins-and-grants).

The Vault publishes [`VaultApi`](sdk-reference.html#dev.jasper.vault.api.VaultApi) with `publishPerConsumer`, so each
consumer's instance carries that plugin's identity and every grant names it. Call every method on the UI thread; each
returns at once, and every future completes on the UI thread. Cancelling a future withdraws that request, and a prompt
nobody waits for any more closes.

**Grants.** A grant lets one plugin read one credential. The first `credential` request for it asks the user,
"Allow *plugin* to use *credential*?", with **Deny**, **Always allow for *plugin*** and **Allow once**; Enter presses
none of them, and Escape or closing the dialog denies. Requests from one plugin for one credential share one dialog,
which denies when the last of them is cancelled. **Always** saves the grant in the vault; **Allow once** answers the
waiting requests without saving anything, so the next request asks again; a choice in either picker gives a one-time
grant that the next fetch uses up. Locking the vault forgets one-time grants and denies or cancels every open grant,
pick and import prompt. Saved grants are listed, and revoked, under **Vault Plugin Grants...** in the File menu;
deleting an entry removes its grants.

| Member | Does | Answers and fails |
| --- | --- | --- |
| [`LOCK_STATE_CHANGED`](sdk-reference.html#dev.jasper.vault.api.VaultApi.LOCK_STATE_CHANGED) | The [`Topic`](sdk-reference.html#dev.jasper.sdk.events.Topic) `dev.jasper.vault.lock-state`, published at every lock transition, including the lock when the Vault stops. Subscribe with `context.events().subscribe`; read `lockState()` first, since events are not replayed. | A [`LockState`](sdk-reference.html#dev.jasper.vault.api.LockState) |
| [`lockState()`](sdk-reference.html#dev.jasper.vault.api.VaultApi.lockState()) | The current state. | `NO_VAULT`, `LOCKED` or `UNLOCKED` |
| [`ensureUnlocked(owner)`](sdk-reference.html#dev.jasper.vault.api.VaultApi.ensureUnlocked(WindowHandle)) | Asks for the master password over `owner` when locked. One unlock prompt serves every requester; it closes when its last waiter cancels. | `true` once unlocked; `false` when cancelled, or with no vault, which posts the warning "No vault yet" / "Create a vault in Credential Vault first (Open Vault...)" |
| [`credentials()`](sdk-reference.html#dev.jasper.vault.api.VaultApi.credentials()) | Every login, path key and stored key, without secrets. Needs no grant; secure notes are never listed. | [`CredentialDescriptor`](sdk-reference.html#dev.jasper.vault.api.CredentialDescriptor)s; empty unless unlocked |
| [`credential(owner, id)`](sdk-reference.html#dev.jasper.vault.api.VaultApi.credential(WindowHandle,UUID)) | Unlocks over `owner` if needed, then asks for a grant unless one exists. Prompts open over `owner`, except that the unlock prompt opens in the Credential Vault window while that is open, and a request joins an unlock or grant prompt already open elsewhere; if that prompt's window closes while `owner` is still open, it asks again over `owner`. Each waiting request gets its own copy. | A [`Credential`](sdk-reference.html#dev.jasper.vault.api.Credential), or empty when denied, cancelled, the id is unknown, `owner` is null or closed, or there is no vault (which posts "No vault yet"); a credential that arrives after `owner` closed is closed and the answer is empty |
| [`credential(id)`](sdk-reference.html#dev.jasper.vault.api.VaultApi.credential(UUID)) | The same, prompting over the window this plugin last passed to the Vault while it is open, else the active or first workspace window. | As above; empty, with the warning "Credential Vault has no window to ask in", when the vault is locked and there is no window |
| [`pick(owner, request)`](sdk-reference.html#dev.jasper.vault.api.VaultApi.pick(WindowHandle,CredentialPickRequest)) | Vault's chooser in a popover on the request's [`UiAnchor`](sdk-reference.html#dev.jasper.sdk.ui.UiAnchor), after unlocking: **Search credentials**, a **None** row when the request allows clearing and the search is empty, one row per credential and **New Credential…**, which opens Vault's editor for a new entry and then the chooser again; creating an entry neither selects nor grants it. | A [`CredentialPickResult`](sdk-reference.html#dev.jasper.vault.api.CredentialPickResult): `Chosen` with the descriptor and a one-time grant, `Cleared` for **None**, or `Cancelled` when the popover is dismissed, the anchor closes, the vault locks, unlocking is cancelled or there is no vault. Throws `IllegalArgumentException` ("Foreign or expired anchor") for a closed anchor or one Jasper did not issue |
| [`pick(owner)`](sdk-reference.html#dev.jasper.vault.api.VaultApi.pick(WindowHandle)) | A modal "Choose a credential" dialog over `owner`, after unlocking. | The chosen descriptor, with a one-time grant, or empty when cancelled or there is no vault |
| [`importSshKeys(owner, sources, selectedCredentials)`](sdk-reference.html#dev.jasper.vault.api.VaultApi.importSshKeys(WindowHandle,List,List)) | Creates or unlocks the vault, reads each key on a background thread (asking for a passphrase when needed), shows **Import SSH keys** with "Import and allow *plugin* to use these credentials", then stores each key in the vault: a stored key with the same fingerprint is reused, and a path key with the same fingerprint becomes a stored key with the same id. Saves a lasting grant for the calling plugin on every imported key and every selected credential, without a grant dialog. | An [`SshKeyImportResult`](sdk-reference.html#dev.jasper.vault.api.SshKeyImportResult), or empty when the user cancels, or `owner` closes or the vault locks before the save starts; when either happens during the save the future is cancelled, though the save may still finish (the Vault then posts "Host import was cancelled"). Throws `IllegalArgumentException` ("Invalid key import batch") for more than 4096 sources or a repeated request id. Fails with `IllegalArgumentException` ("Could not import *file*: unreadable, unsupported or mismatched key", "Choose a stored Vault key or password; import file-based keys first", "Could not open Vault", "Could not start key import") or `IllegalStateException` ("Could not finish saving imported credentials; check Vault before retrying") |

The interface's default bodies serve other implementations, such as test fakes: `credential(owner, id)` delegates to
`credential(id)`, `pick(owner, request)` maps `pick(owner)` to `Chosen` or `Cancelled`, and `importSshKeys` fails with
`UnsupportedOperationException`. The Vault overrides all three.

| Type | What it is |
| --- | --- |
| [`Credential`](sdk-reference.html#dev.jasper.vault.api.Credential) | The caller's own copy of one secret, `AutoCloseable`. Fetch it just before use and close it right after: `close()` zeroes the password, passphrase and key bytes, and every accessor afterwards throws `IllegalStateException` ("The credential is closed"). The vault keeps nothing about it, so locking never invalidates one in hand. `password()`, `passphrase()` and `keyBytes()` return the arrays themselves, not copies. The public constructors are for the Vault and for test fakes; giving both a key path and key bytes throws `IllegalArgumentException`. |
| [`CredentialDescriptor`](sdk-reference.html#dev.jasper.vault.api.CredentialDescriptor) | `(id, name, subtitle, kind, managedKey)`, without the secret: `subtitle` is the username for a login and the fingerprint for a key; `managedKey` is true for a key stored in the vault. |
| [`Kind`](sdk-reference.html#dev.jasper.vault.api.Kind) | `ACCOUNT_PASSWORD`, `ACCOUNT_KEY`, `ACCOUNT_KEY_AND_PASSWORD`, `SSH_KEY`. |
| [`LockState`](sdk-reference.html#dev.jasper.vault.api.LockState) | `NO_VAULT`, `LOCKED`, `UNLOCKED`. |
| [`CredentialPickRequest`](sdk-reference.html#dev.jasper.vault.api.CredentialPickRequest) | `(anchor, current, allowClear)`: where the chooser attaches (an anchor from [`UiView.anchor`](sdk-reference.html#dev.jasper.sdk.ui.UiView) on a mounted action or picker of the consumer's view), the credential to mark as current, and whether **None** is offered. It grants nothing. |
| [`CredentialPickResult`](sdk-reference.html#dev.jasper.vault.api.CredentialPickResult) | Sealed: [`Chosen`](sdk-reference.html#dev.jasper.vault.api.CredentialPickResult.Chosen)`(credential)`, [`Cleared`](sdk-reference.html#dev.jasper.vault.api.CredentialPickResult.Cleared)`()`, [`Cancelled`](sdk-reference.html#dev.jasper.vault.api.CredentialPickResult.Cancelled)`()`. Only `Chosen` carries a grant. |
| [`CredentialPresentation`](sdk-reference.html#dev.jasper.vault.api.CredentialPresentation) | How Vault shows a credential, for a consumer's own picker: `face(descriptor, density)` for a picker's face (a null descriptor gives an empty face with the `lock` icon), `item(descriptor)` for a row, `caption(descriptor)`. The icon is `user` for a password login and `key` otherwise, on a blue or green hue; the caption is "Password", "Account key", "Key and password", "Stored key" or "Path key", then " · *subtitle*". |
| [`SshKeySource`](sdk-reference.html#dev.jasper.vault.api.SshKeySource) | `(requestId, name, path)`: one private key to import. The name must not be blank ("A key needs a name"); the path is made absolute and normalized; `requestId` ties the key to its entry in the result. |
| [`SshKeyImportResult`](sdk-reference.html#dev.jasper.vault.api.SshKeyImportResult) | `(keys, selectedCredentials)`: each source's `requestId` mapped to its stored key's descriptor, and the descriptors of the selected credentials. |

What a `Credential` holds, by kind:

| Kind | `username()` | `password()` | `keyPath()` | `keyBytes()` | `passphrase()` |
| --- | --- | --- | --- | --- | --- |
| `ACCOUNT_PASSWORD` | yes | yes | empty | empty | null |
| `ACCOUNT_KEY` | yes | null | yes | empty | when set |
| `ACCOUNT_KEY_AND_PASSWORD` | yes | yes | yes | empty | when set |
| `SSH_KEY`, stored key | empty | null | empty | the key file's contents | when set |
| `SSH_KEY`, path key | empty | null | yes | empty | null |

**Bundled consumers.** Remote signs in to saved hosts with `credential(owner, id)`, attaches `pick(owner, request)` to
the credential field of its host editor, imports keys from `~/.ssh/config` with `importSshKeys`, and labels hosts'
credentials from `credentials()` and the lock state ([Remote](remote.md)). File Server resolves its servers' Vault
logins with `credential` and attaches the picker to its server editor ([File Server](file-server.md)). Both refresh
their panels on `LOCK_STATE_CHANGED` and declare `{ id = "dev.jasper.vault", version = ">=0.2", optional = true }`.

**Recipe.** Fetch a secret just before use and close it when done; here the sign-in itself, `logIn`, is the plugin's
own blocking call:

```java
private Optional<VaultApi> vault = Optional.empty();

@Override public void start(PluginContext context) {
    this.context = context;
    try { vault = context.services().find(VaultApi.class); }
    catch (NoClassDefFoundError absent) { vault = Optional.empty(); }
    if (vault.isPresent()) context.events().subscribe(VaultApi.LOCK_STATE_CHANGED, state -> refresh());
}

/** UI thread: sign in with the credential the user chose earlier. */
private void signIn(WindowHandle window, UUID credentialId) {
    if (vault.isEmpty()) return;
    vault.get().credential(window, credentialId).thenAccept(found -> found.ifPresent(credential ->
        context.background().execute(() -> {
            try (credential) { logIn(credential.username().orElseThrow(), credential.password()); }
        })));
}

/** UI thread: the user pressed the "credential" picker in this plugin's view. */
private void chooseCredential(WindowHandle window, UiView view, Optional<UUID> current) {
    if (vault.isEmpty()) return;
    vault.get().pick(window, new CredentialPickRequest(view.anchor("credential"), current, true)).thenAccept(result -> {
        switch (result) {
            case CredentialPickResult.Chosen chosen -> remember(chosen.credential().id());
            case CredentialPickResult.Cleared cleared -> forget();
            case CredentialPickResult.Cancelled cancelled -> { }
        }
    });
}
```

## Editor API

Package [`dev.jasper.editor.api`](sdk-reference.html#package-dev.jasper.editor.api), from the Editor
(`dev.jasper.editor`, 0.10.0). The user's side is in [Editor](editor.md).

The Editor publishes one [`Editor`](sdk-reference.html#dev.jasper.editor.api.Editor) for every consumer with `publish`.
Call it on the UI thread only. Documents open as Editor tabs in a workspace window; opening a document that is already
open selects its tab, keeps it in the window it is in, and raises that window.

| `Editor` member | Does |
| --- | --- |
| [`open(Path file)`](sdk-reference.html#dev.jasper.editor.api.Editor.open(Path)) | Opens a local file, or selects the tab showing it. |
| [`open(EditorDocument document)`](sdk-reference.html#dev.jasper.editor.api.Editor.open(EditorDocument)) | Opens a document the calling plugin reads and writes, or selects the tab whose document has the same `key()`. |
| [`open(Path file, WindowOwner owner)`](sdk-reference.html#dev.jasper.editor.api.Editor.open(Path,WindowOwner)), [`open(EditorDocument document, WindowOwner owner)`](sdk-reference.html#dev.jasper.editor.api.Editor.open(EditorDocument,WindowOwner)) | The same, in the workspace window `owner` belongs to, or the last main workspace when `owner` is null. |
| [`reopener(keyPrefix, resolve)`](sdk-reference.html#dev.jasper.editor.api.Editor.reopener(String,Function)) | Makes **Recent Files** entries whose key starts with `keyPrefix` reopenable. `resolve` gets a recorded key, on the UI thread, and must do no I/O: it answers empty when the key cannot be reopened now (its host was deleted, say), otherwise a function that builds the document for the window reopening it. A resolver that throws or answers null cannot reopen. A later registration of the same prefix replaces the earlier one, and the longest matching prefix wins. The prefix may not be blank or start with `local:`, the Editor's own (`IllegalArgumentException`, "Not a key prefix: …"). |
| [`opener(handler)`](sdk-reference.html#dev.jasper.editor.api.Editor.opener(Predicate)) | Lets a plugin answer **Open in Editor…** itself, for example by browsing a remote host. Handlers get an [`OpenRequest`](sdk-reference.html#dev.jasper.editor.api.OpenRequest) on the UI thread, in registration order, before the file chooser; the first to return true has handled it. One that throws is logged and counts as false. |
| [`locationDocuments(sourceId, resolver)`](sdk-reference.html#dev.jasper.editor.api.Editor.locationDocuments(String,LocationResolver)) | While registered, **Open in Editor…** and **Save As…** offer the roots of the file source `sourceId` and open or save what the user picks there through the [`LocationResolver`](sdk-reference.html#dev.jasper.editor.api.LocationResolver). A later registration for the same source replaces the earlier one; a blank id throws `IllegalArgumentException`. |

`reopener`, `opener` and `locationDocuments` return a [`Subscription`](sdk-reference.html#dev.jasper.sdk.Subscription)
that removes the registration. Their default bodies do nothing, for other implementations such as test fakes.

| Type | What it is |
| --- | --- |
| [`EditorDocument`](sdk-reference.html#dev.jasper.editor.api.EditorDocument) | Implemented by the calling plugin: a file the Editor reads and writes through it. The Editor calls its methods on the UI thread and none may block; its futures may complete on any thread. `key()` is its identity (equal keys are the same file, and **Recent Files** records it), starting with the plugin's own prefix and never `local:`; `name()` the tab title; `location()` the full location for tooltips and the status line, such as `dustin@prod-web:/etc/nginx/nginx.conf`; `host()` the host shown dimmed after the name and in the loading text, empty for a local document. `read(limitBytes)` returns the content, failing with `TooLargeException` before transferring anything when the file is larger; the Editor passes its `max_file_mib` setting in bytes (default 10 MiB). Cancelling the future aborts the transfer. `revision()` returns the current revision, empty when the file no longer exists; the Editor asks for it before each Save (not Save As) and, when it differs from the revision last read or written, lets the user overwrite or reload, or, when the file is gone, save anyway. `write(content)` replaces the content and returns the new revision, following the save contract in its Javadoc: resolve symbolic links; write a hidden sibling with the target's permissions and replace the target atomically; write in place when the sibling cannot be created, would change owner or group, the target has more than one hard link, or atomic replacement is unavailable; create a missing target; then stat it. |
| [`Content`](sdk-reference.html#dev.jasper.editor.api.Content) | `(bytes, revision)`: the array passes to the receiver, and the provider must not reuse it. |
| [`Revision`](sdk-reference.html#dev.jasper.editor.api.Revision) | `(modifiedMillis, size)`: two reads with equal revisions are the same content. |
| [`TooLargeException`](sdk-reference.html#dev.jasper.editor.api.TooLargeException) | An `IOException` with `size()` and `limit()`: "The file is *size* bytes; the limit is *limit* bytes". |
| [`LocationResolver`](sdk-reference.html#dev.jasper.editor.api.LocationResolver) | `document(location, owner)` builds the document for a [`FileLocation.Sourced`](sdk-reference.html#dev.jasper.sdk.files.FileLocation.Sourced) the user picked; `location(documentKey)` says where an open document is in the source, so the chooser starts in its folder. UI thread, no I/O. A `document` that throws posts "Could not open a file", or "Could not save a file" from **Save As…**. |
| [`OpenRequest`](sdk-reference.html#dev.jasper.editor.api.OpenRequest) | `(window, pane, documentKey)`: the workspace window; the focused terminal pane, empty while another kind of tab is selected; and the active Editor tab's key when no pane is focused. The pane handle is the Editor's and limited by the Editor's capabilities: use its `id()` to find the pane through your own terminals. It is not the SDK's `dev.jasper.sdk.terminal.OpenRequest`. |

**Bundled consumers.** Remote opens SFTP files as `EditorDocument`s with keys `remote:<host id>/<login>[,…]:<path>`,
registers `reopener("remote:", …)` so **Recent Files** reopens them, and `locationDocuments` for its saved hosts' file
source `dev.jasper.remote.hosts` ([Remote](remote.md#editing-remote-files)). Project Index opens indexed local files with
`open(path, window)` ([Project Index](project-index.md)). Remote declares `>=0.2` and Project Index `>=0.7`, both
optional.

**Recipe.** Open a local file in the window an action was invoked in:

```java
private Optional<Editor> editor = Optional.empty();

@Override public void start(PluginContext context) {
    try { editor = context.services().find(Editor.class); }
    catch (NoClassDefFoundError absent) { editor = Optional.empty(); }
    Path config = context.dataDirectory().resolve("tool.conf");
    context.actions().register(ActionSpec.of("dev.example.tool.edit_config", "Edit Tool Config"), invoked -> {
        if (editor.isPresent()) editor.get().open(config, invoked.window());
        else context.notifications().group().warning("No Editor", "Install the Editor plugin to edit tool.conf.");
    });
}
```

A document from the plugin's own store; `Store` stands for the plugin's blocking client, and the Editor's futures run
on [`background()`](sdk-reference.html#dev.jasper.sdk.plugin.PluginContext):

```java
final class StoreDocument implements EditorDocument {
    interface Store {
        Optional<Revision> stat(String path) throws IOException;
        byte[] read(String path) throws IOException;
        Revision write(String path, byte[] content) throws IOException;
    }

    private final Store store;
    private final String path;
    private final Executor background;

    StoreDocument(Store store, String path, Executor background) {
        this.store = store; this.path = path; this.background = background;
    }

    @Override public String key() { return "dev.example.tool:" + path; }
    @Override public String name() { return path.substring(path.lastIndexOf('/') + 1); }
    @Override public String location() { return "tool:" + path; }
    @Override public Optional<String> host() { return Optional.of("tool"); }

    @Override public CompletableFuture<Content> read(long limitBytes) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Revision revision = store.stat(path).orElseThrow(() -> new NoSuchFileException(path));
                if (revision.size() > limitBytes) throw new TooLargeException(revision.size(), limitBytes);
                return new Content(store.read(path), revision);
            } catch (IOException failure) { throw new CompletionException(failure); }
        }, background);
    }

    @Override public CompletableFuture<Optional<Revision>> revision() {
        return CompletableFuture.supplyAsync(() -> {
            try { return store.stat(path); } catch (IOException failure) { throw new CompletionException(failure); }
        }, background);
    }

    @Override public CompletableFuture<Revision> write(byte[] content) {
        return CompletableFuture.supplyAsync(() -> {
            try { return store.write(path, content); } catch (IOException failure) { throw new CompletionException(failure); }
        }, background);
    }
}
```

Open it with `editor.get().open(new StoreDocument(store, path, context.background()), window)`, and make its recent
entries reopenable. Make both calls from a class the plugin creates only once it found the Editor
([when the provider is absent](#when-the-provider-is-absent)):

```java
reopener = editor.get().reopener("dev.example.tool:", key -> Optional.of(
    owner -> new StoreDocument(store, key.substring("dev.example.tool:".length()), context.background())));
```

## Remote API

Package [`dev.jasper.remote.api`](sdk-reference.html#package-dev.jasper.remote.api), from Remote (`dev.jasper.remote`,
0.14.0). The user's side is in [Remote](remote.md).

Remote publishes one [`RemoteProjects`](sdk-reference.html#dev.jasper.remote.api.RemoteProjects) for every consumer
with `publish`. Call every method on the UI thread. A stage completes on a worker thread once its remote work has
started; one that fails before then (no open connection, or a failed sign-in) completes on the UI thread and may
already be complete when returned. A failure may arrive wrapped in a `CompletionException`. The API names only JDK
and SDK types. A method without a window never connects and never asks the user to sign in; `list` with a window,
`open` and `openTerminal` may do both.

| `RemoteProjects` member | Does | Answers and fails |
| --- | --- | --- |
| [`locate(paneId)`](sdk-reference.html#dev.jasper.remote.api.RemoteProjects.locate(UUID)) | Where an SSH pane Remote opened is. | A [`RemoteFolder`](sdk-reference.html#dev.jasper.remote.api.RemoteFolder) with the saved host and folder, or empty when the pane is not one of Remote's or its folder is unknown |
| [`connected(hostId)`](sdk-reference.html#dev.jasper.remote.api.RemoteProjects.connected(UUID)) | Whether an authenticated connection to the saved host is open, so background work can use it. | `boolean` |
| [`hostName(hostId)`](sdk-reference.html#dev.jasper.remote.api.RemoteProjects.hostName(UUID)) | The saved host's name. | Empty when the host is no longer saved |
| [`fileSourceId()`](sdk-reference.html#dev.jasper.remote.api.RemoteProjects.fileSourceId()) | The id of Remote's file source, for the sources of a [`FileChoice`](sdk-reference.html#dev.jasper.sdk.files.FileChoice). | `"dev.jasper.remote.hosts"` |
| [`folder(location)`](sdk-reference.html#dev.jasper.remote.api.RemoteProjects.folder(FileLocation.Sourced)) | The host and folder of a [`FileLocation.Sourced`](sdk-reference.html#dev.jasper.sdk.files.FileLocation.Sourced) the file chooser returned from Remote's source. | Empty for another source, a root that is not a saved host, or a path that is not absolute (an unresolved home folder) |
| [`exists(hostId, folders, names)`](sdk-reference.html#dev.jasper.remote.api.RemoteProjects.exists(UUID,List,Set)) | Which of `names` exist in which absolute `folders`, in one `sh -s` command over the open connection. Names containing `*` are globs. The login's home folder and its ancestors report nothing. | A map of folder to the names found there. Fails with [`NotConnectedException`](sdk-reference.html#dev.jasper.remote.api.NotConnectedException) without an open connection, and with an `IOException` when the command fails ("Could not look for project markers on *host*") or runs over 10 seconds |
| [`list(hostId, root, options, signIn)`](sdk-reference.html#dev.jasper.remote.api.RemoteProjects.list(UUID,String,ListOptions,Optional)) | A project's files under the absolute `root`: Git's view of the work tree (tracked and untracked files, without ignored ones; not when the work tree's top is the login's home folder), else `find`, else an SFTP walk. With `signIn`, it connects in that window when needed. | A [`RemoteListing`](sdk-reference.html#dev.jasper.remote.api.RemoteListing). Fails with `NotConnectedException` when `signIn` is empty and no connection is open, `NoSuchFileException` when the root cannot be entered, an `IOException` when the timeout passes, and `CancellationException` when `options.cancelled()` returns true |
| [`open(hostId, path, window)`](sdk-reference.html#dev.jasper.remote.api.RemoteProjects.open(UUID,String,WindowHandle)) | Opens the remote file in the Editor, connecting and asking to sign in when needed; reports failures itself. | Nothing; does nothing for a host that is no longer saved. Without the Editor, posts "Could not open a remote file" / "Install the Editor plugin to open *path*." |
| [`openTerminal(hostId, folder, window)`](sdk-reference.html#dev.jasper.remote.api.RemoteProjects.openTerminal(UUID,String,WindowHandle)) | Opens an SSH tab on the host in `window`, whose shell starts in `folder`; may connect and ask to sign in. | Nothing; does nothing for a host that is no longer saved |

| Type | What it is |
| --- | --- |
| [`RemoteFolder`](sdk-reference.html#dev.jasper.remote.api.RemoteFolder) | `(hostId, hostName, path)`: a folder or file on a saved host. A path that does not start with `/` throws `IllegalArgumentException`. |
| [`ListOptions`](sdk-reference.html#dev.jasper.remote.api.ListOptions) | `(excludes, maxFiles, timeout, cancelled)`: folder names never listed, the most files to return (at least 1, else `IllegalArgumentException`), the time all strategies together may take, and a check polled from a worker thread while listing. |
| [`RemoteListing`](sdk-reference.html#dev.jasper.remote.api.RemoteListing) | `(files, strategy, truncated)`: `/`-separated paths relative to the root, how they were found ([`Strategy`](sdk-reference.html#dev.jasper.remote.api.RemoteListing.Strategy) `GIT`, `FIND` or `SFTP`), and whether the listing stopped at `maxFiles`. |
| [`NotConnectedException`](sdk-reference.html#dev.jasper.remote.api.NotConnectedException) | An `IOException`: "*host* is not connected". Background work found no authenticated connection and may not open one. |

**Bundled consumer.** Project Index uses every member: it locates an SSH pane's folder, detects the project root with
`exists`, lists it with `list` (a two-minute timeout; a sign-in window only for a job the user asked for), takes remote
folders from the chooser with `fileSourceId` and `folder`, and opens files and terminals with `open` and
`openTerminal` ([Project Index](project-index.md)). It declares `{ id = "dev.jasper.remote", version = ">=0.12",
optional = true }`.

**Recipe.** Count the files of the project an SSH pane is in:

```java
private Optional<RemoteProjects> remote = Optional.empty();

// in start
try { remote = context.services().find(RemoteProjects.class); }
catch (NoClassDefFoundError absent) { remote = Optional.empty(); }

/** An action handler, on the UI thread. */
private void countFiles(ActionContext invoked) {
    if (remote.isEmpty() || invoked.pane().isEmpty()) return;
    RemoteProjects projects = remote.get();
    Optional<RemoteFolder> here = projects.locate(invoked.pane().get().id());
    if (here.isEmpty()) return;
    var options = new ListOptions(List.of(".git", "node_modules"), 50_000, Duration.ofMinutes(2), () -> false);
    projects.list(here.get().hostId(), here.get().path(), options, Optional.of(invoked.window()))
        .whenComplete((listing, failure) -> {   // a worker thread, or the UI thread when it failed before connecting; notifications may be posted from any thread
            var notices = context.notifications().group();
            if (failure != null) notices.error("Could not list " + here.get().path(), failure);
            else notices.info(here.get().hostName(), listing.files().size() + (listing.truncated() ? "+ files" : " files"));
        });
}
```

## Snippets API

Package [`dev.jasper.snippets.api`](sdk-reference.html#package-dev.jasper.snippets.api), from Snippets
(`dev.jasper.snippets`, 0.3.0). The user's side is in [Shell History and Snippets](history-and-snippets.md).

Snippets publishes one [`SnippetService`](sdk-reference.html#dev.jasper.snippets.api.SnippetService) for every consumer
with `publish`. Call both methods on the UI thread.

| `SnippetService` member | Does |
| --- | --- |
| [`SCOPE_ID`](sdk-reference.html#dev.jasper.snippets.api.SnippetService.SCOPE_ID) | `"dev.jasper.snippets.scope"`, the Snippets palette scope, for [`PaletteStep.Result.reopen`](sdk-reference.html#dev.jasper.sdk.palette.PaletteStep.Result) or [`Palette.open`](sdk-reference.html#dev.jasper.sdk.palette.Palette). |
| [`byName(name)`](sdk-reference.html#dev.jasper.snippets.api.SnippetService.byName(String)) | The snippet with that name, compared without regard to case, as of the last successful read of `snippets.toml`; empty otherwise. |
| [`append(name, command, done)`](sdk-reference.html#dev.jasper.snippets.api.SnippetService.append(String,String,BiConsumer)) | Appends a snippet to `snippets.toml` on Snippets' own worker thread, then calls `done` on the UI thread with exactly one of the saved snippet and a message: "A snippet named *name* exists", "snippets.toml has errors; fix it and Reload Config before saving", or "Could not save snippet: *reason*". The name, without surrounding spaces, must be 1 to 128 printable characters ("Snippet names are 1–128 printable characters"); the command must not be blank ("Snippet needs a command") and is at most 16 KiB. |
| [`rowId(name)`](sdk-reference.html#dev.jasper.snippets.api.SnippetService.rowId(String)) | Static: the palette row id the Snippets scope gives that name, so a consumer can select the snippet when it reopens the scope. |

[`SnippetView`](sdk-reference.html#dev.jasper.snippets.api.SnippetView) is `(name, command, keywords)`: the name, unique
without regard to case; the command, which may hold `{{placeholders}}`; and extra search words.

**Bundled consumer.** Shell History offers **Save as snippet…** (Shift+Enter in the History scope) only when it found
the service: a step titled "Save as snippet: *command*" asks for the name, then reopens the Snippets scope on the new
snippet, or shows the message ([Shell History and Snippets](history-and-snippets.md)). It declares
`{ id = "dev.jasper.snippets", version = ">=0.1", optional = true }`, but its lookup does not catch
`NoClassDefFoundError`, so Shell History fails to start when Snippets is not loaded.

**Recipe.** Save a command and show it in the Snippets scope (opening the palette needs `palette.contribute`):

```java
private Optional<SnippetService> snippets = Optional.empty();

// in start
try { snippets = context.services().find(SnippetService.class); }
catch (NoClassDefFoundError absent) { snippets = Optional.empty(); }

/** UI thread. */
private void save(WindowHandle window, String name, String command) {
    if (snippets.isEmpty()) return;
    snippets.get().append(name, command, (saved, message) -> {   // UI thread
        if (saved.isPresent()) context.palette().open(window, SnippetService.SCOPE_ID, Optional.empty(),
            Optional.of(SnippetService.rowId(saved.get().name())));
        else context.notifications().group().warning("Snippet not saved", message.orElseThrow());
    });
}
```

## Enterprise API

Package [`dev.jasper.enterprise.api`](sdk-reference.html#package-dev.jasper.enterprise.api), from Enterprise
(`dev.jasper.enterprise`, 0.2.0). The user's side is in [the Enterprise look](enterprise.md).

[`Workstation`](sdk-reference.html#dev.jasper.enterprise.api.Workstation) is an empty interface: its presence is the
message. Enterprise is off by default and starts only as the active look, and its `start` does nothing but publish
`Workstation`. A plugin that finds it composes its classic layout for that run, and its default layout otherwise. The
look changes only at a restart, so read it once in `start`.

**Bundled consumers.** Credential Vault, Remote and File Server each declare
`{ id = "dev.jasper.enterprise", version = ">=0.1", optional = true }` and read a `Layout` once in `start`
([classic layouts](enterprise.md#classic-layouts-in-remote-file-server-and-credential-vault)).

**Recipe.**

```java
enum Layout {
    DEFAULT, CLASSIC;

    static Layout of(Services services) {
        try { return services.find(Workstation.class).isPresent() ? CLASSIC : DEFAULT; }
        catch (NoClassDefFoundError absent) { return DEFAULT; }
    }
}
```

## Exporting your own API

1. Put the API in a package of its own, for example `dev.example.tool.api`, and list it under `exports`. Keep the
   implementation in other packages.
2. Name only JDK types, SDK types and the package's own types in it. A consumer can load nothing else of yours: not
   your other packages and not the libraries you bundle.
3. Publish in `start`, on the UI thread:

   ```java
   context.services().publish(ToolApi.class, new ToolService(...));
   ```

   or, so that each consumer's instance knows who calls it, as Credential Vault does:

   ```java
   context.services().publishPerConsumer(ToolApi.class, consumer -> new ToolService(consumer.id(), ...));
   ```

   The factory runs once per consumer, on the UI thread, just before that consumer starts. Its
   [`PluginInfo`](sdk-reference.html#dev.jasper.sdk.PluginInfo) attributes calls; it is not a security boundary.
4. Publishing fails when:

   | Problem | Thrown |
   | --- | --- |
   | Outside the provider's `start` | `IllegalStateException`: "Services may be published only during start(): *id*" |
   | Off the UI thread | `IllegalStateException`: "publish must be called on the UI thread: *id*" |
   | The API is a class | `IllegalArgumentException`: "A service API must be an interface: …" |
   | The API is not loaded by the provider from one of its exported packages | `IllegalArgumentException`: "… is not from one of *id*'s exported packages" |
   | Another plugin, or this one, already published the interface | `IllegalArgumentException`: "Service already published: …" |

   Publications become visible only when `start` returns normally; a failed start discards them. There is no
   withdrawal: a publication lasts until shutdown.
5. To tell consumers about changes, declare a [`Topic`](sdk-reference.html#dev.jasper.sdk.events.Topic) on the API
   whose id starts with your plugin id and a dot, for example
   `Topic<State> CHANGED = Topic.of("dev.example.tool.changed", State.class);`, and publish it from any thread with
   `context.events().publish(ToolApi.CHANGED, state)`, as Credential Vault does with `LOCK_STATE_CHANGED`. Handlers
   run later on the UI thread, and events are not replayed, so offer a getter for the current state too. Publishing to
   any other topic throws `IllegalArgumentException` ("*id* may not publish to *topic*").
6. Say in the Javadoc which thread calls each method, which thread completes each future, and what a consumer-supplied
   interface (such as `EditorDocument`) may and may not do.
7. Mark each method added after the API's first version with the version that added it, so consumers know which calls
   to guard against an older provider.
