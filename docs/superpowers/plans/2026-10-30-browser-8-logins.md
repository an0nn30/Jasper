# Browser 8: logins — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or
> superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The Credential Vault plays iTerm2's password manager in the Browser:
- 🔑 and 🎫 buttons on a page's password and username fields open the Vault's picker, and the chosen login fills them;
- HTTP sign-ins (Basic, Digest, NTLM) get Jasper's dialog, with **Password Manager** and **Remember this password**;
- a remembered login fills without the dialog while the Vault is unlocked and the grant stands;
- **Manage Saved Passwords** in Settings › Browser opens the Vault manager.

Without the Vault plugin none of the Vault parts appear.

**Architecture:**
- **The SDK** gets an anchor on an area of custom content (`UiView.anchorAt`), so a popover can open at a point on a
  page. `UiAnchor` covers only a mounted control today.
- **The Vault's API** gets a starting search for its picker, `saveLogin` (with a saved grant for the saving plugin),
  `credentialIfGranted` (no prompt) and `openManager`.
- **The Browser** gets a package, `logins`, that names no Chromium type. `VaultLogins` is its only class that names
  `dev.jasper.vault.api`. `FieldButtons` and the page script `login-fields.js` make the field buttons, and
  `Challenge`, `SignInDialog` and `HttpSignIns` handle HTTP sign-ins.
- **Chromium stays in `page` and `links`,** which hand `logins` plain types: page-script requests through the opener
  bridge's router, and `getAuthCredentials` from the client's one request handler.

**Tech Stack:** Java 25 and Swing on JBR 25.0.4.1 b635.75 with JCEF, Jasper's SDK and testkit, the Vault plugin's
exported API, and JUnit 5.

**Spec:** [docs/superpowers/specs/2026-10-26-jasper-browser-plugin-design.md](../specs/2026-10-26-jasper-browser-plugin-design.md):
section 13, section 17 (Manage Saved Passwords), section 12 (no HTTP sign-in remembered in /dev/null mode), section 21
(frame sign-ins told apart by origin only) and section 23, item 8. iTerm2's behaviour is in
`~/projects/moray/.superpowers/iterm2-browser-features-A.md`, item 8. Research (2026-10-30, through git at `main`
`5c7e97279`, `claude/browser-core` `fc3d60270` and JBR's `javap`) is summarised below.

## Global Constraints

- **Branch:** from `main` once plans 4b to 7 have merged, in its own worktree (`claude/browser-logins`). Plan 4a's
  rulings R0-R48 bind, and so do those in the banners of plans 4b to 7.
- **Versions:**
  - the SDK addition takes the next minor after `JasperSdk.VERSION` on the branch it starts from, with that `@since`,
    and every bundled manifest's range moves with it;
  - each changed plugin moves to its next minor: the Vault (0.8.0 on `main` today, so 0.9.0) and the Browser;
  - the Vault's exported API carries no `@since`.
- **Names from earlier plans** (follow any renames their banners record):
  - **4a:** `Page` and its hook style (`onOpenItem`, `onClose`), `PageBrowser.owner()`, `PageView`,
    `Activation.consume(long)`, `DialogPlace`, `PageEvents.onMainFrameLoadStart`, `PageNavigation.Links` (the client's
    only request handler, which hands main-frame navigations to `OpenerBridge.Responses`), `OpenerBridge`'s `Answer`,
    codes (`MALFORMED` 1, `REFUSED` 2, `GONE` 3, `BUSY` 5), `Sender` and `Document.origin()`, `BrowserItem.PAGE`
    (`"browser.page"`, the Custom node), `BrowserItems`, `ScriptDialog`, and `ChromiumBoundaryTest`'s scan;
  - **4b:** how its router ops `favicon` and `find` are registered and its scripts injected (this plan's follow
    suit), and the item's zoom (`Zoom`, a percent);
  - **6:** an item's /dev/null mode (written `devNull()` below), the Settings page whose Privacy & Data group holds
    Manage Site Permissions, and whatever 6 made of the main-frame resource handler.
- **JCEF facts** (`javap --module jcef`, 2026-10-30; do not re-derive):
  - `CefRequestHandler.getAuthCredentials(CefBrowser, String originUrl, boolean isProxy, String host, int port,
    String realm, String scheme, CefAuthCallback)` has no frame argument. It is called on Chromium's IO thread;
    returning true means Jasper answers later, from any thread, with `CefAuthCallback.Continue(String, String)` or
    `cancel()`. JCEF keeps a client's first request handler (4a), so it goes in `Links`.
  - `CefMessageRouterHandler.onQueryCanceled(browser, frame, queryId)` is called when the asking frame navigates or
    closes. `CefQueryCallback` has `success(String)` and `failure(int, String)`.
  - `CefResourceRequestHandler.onResourceRedirect(..., StringRef newUrl)`, `onResourceResponse(...)` (the final
    response) and `CefResponse.getStatus()`.
- **Vault facts** (`main`):
  - **grants:** `VaultService.java:240-249` reads with a saved `Grant(pluginId, credentialId)`, else a one-time grant,
    else asks "Allow *plugin* to use *credential*?" (Deny, Always allow, Allow once); `:148-161` saves Always allow
    through `lock.transact`; `:254-271` gives a pick a one-time grant; `:76-83` forgets one-time grants on lock;
  - **the picker:** `ui/AnchoredPicker.java:24-39` shows `windows().popover(anchor)` and filters name plus caption on
    `query`, which starts empty; the None row shows only while `query` is blank;
  - **default bodies:** `VaultApi.java:42-45, 96, 119-128`. The Remote and File Server tests' `FakeVault`s implement
    `VaultApi`, so new members need default bodies;
  - **the rest:** `model/Account.java:8` (a login, `Auth.Password`, no blank name); Open Vault...'s
    `VaultPlugin.open(window, select)` (`VaultPlugin.java:196-214`); the manager is a non-modal dialog
    (`VaultManagerDialog.java:85`).
- **SDK facts** (`main`):
  - `UiView.anchor(nodeId)` (`UiView.java:15`) uses `KitView.anchorTarget` (`jasper-ui` `swing/KitView.java:96-103`,
    a Button or PICKER only);
  - the host's `HostedAnchor` (`jasper-app` `plugins/HostedAnchor.java:13-44`) and `HostedPopover.fit` and
    `placement` (`:41-49`) put a popover below the target's bounds;
  - the fake is `FakeKitView.Attachment` (`FakeKitView.java:36-46`), `AnchorContractTest` (testFixtures) runs in both
    runtimes, and `FakePluginHost.popoverViews` (`:1434`) lists shown popovers;
  - Settings is modal (`settingsui/SettingsDialog.java:37, 223`).
- **An optional provider:** follow `docs/plugin-apis.md`'s "When the provider is absent" and "Methods newer than the
  provider": the lookup catches `NoClassDefFoundError`, and code naming the API runs only once the service was found,
  as Project Index's `RemoteBridge` does.
- **Boundaries:** `org.cef` stays in the packages `ChromiumBoundaryTest` allows, which exclude `logins`; only
  `logins/VaultLogins` names `dev.jasper.vault` (Task 3's `VaultBoundaryTest`); page scripts run in the top frame only
  (R38) and only in `http:` and `https:` documents.
- **Secrets:** zero every `char[]` once used (JCEF's `Continue` takes `String`s, which cannot be zeroed). A password
  reaches a page only in a router reply, never in a script's source, which DevTools lists. None goes in a log, a
  notification or restore state.
- **Copy** (spec section 13, with "iTerm2" read as "Jasper"): "Open Password Manager"; "The website “HOST” requires a
  user name and password for “REALM”.", without " for “REALM”" when there is no realm; "User name", "Password", "OK",
  "Cancel", "Password Manager", "Remember this password"; "Could not save the password for HOST"; "Manage Saved
  Passwords".
- **No GUI from agents.** Tests are headless and start no Chromium; Task 8 is the user's. Never run `./gradlew --stop`.
- **Commits** end with:

  ```
  🤖 Generated with Claude Code at The Home Depot

  Co-Authored-By: Claude <noreply@anthropic.com>
  ```

## Review Focus

1. **A remembered password that is wrong.** Chromium challenges again after a failed `Continue`. A silent fill is tried
   once per login name until the page's main frame gets a response, so the second challenge shows the dialog instead
   of looping. Task 6 pins this.
2. **The page changes under an open picker** (it navigates, reloads, goes to another origin, or its item closes).
   Nothing is filled, the picker closes, and the fetched credential is closed. Task 4 pins this.
3. **A page script calls the router's login op itself:** with no click, repeatedly, from a sub-frame, or from an
   opaque or `file:` origin. Each is refused, and a page has at most one picker open. Task 4 pins this.
4. **The Vault's states.** Locked: the picker and the save unlock first, and the silent fill falls back to the dialog.
   No vault: "No vault yet" and nothing else. Unlocking cancelled: nothing saved, and no failure notice. The plugin
   absent: no buttons, no Manage Saved Passwords, and a dialog without Password Manager or Remember. Tasks 3, 5 and 6
   pin this.
5. **The item closes, or Jasper quits, mid-flow.** Every waiting challenge is cancelled exactly once, a pending save's
   password is zeroed unsaved, and a late Vault answer is closed unused. Tasks 5 and 6 pin this.

---

### Task 1: The SDK's area anchor

**Files:**
- Modify: `jasper-sdk/src/main/java/dev/jasper/sdk/ui/UiView.java` (`anchorAt`) and `UiAnchor.java` (its Javadoc
  covers an area of a Custom node too).
- Modify: `JasperSdk.java` (`VERSION`), every bundled `plugin.toml`'s range, `docs/examples/external-plugin` and the
  SDK README's compatibility table, as each SDK bump does.
- Modify: `jasper-ui/src/main/java/dev/jasper/ui/swing/KitView.java` (`customTarget`, beside `anchorTarget`).
- Modify: `jasper-app/src/main/java/dev/jasper/app/plugins/HostedKitView.java`, `HostedAnchor.java` and
  `HostedPopover.java`.
- Modify: `jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakeKitView.java` and `FakePluginHost.java`.
- Modify: `docs/plugin-authoring.md:562`, `docs/plugin-ui.md:48`, `docs/sdk-architecture.md:245`, and the guide pages
  `tools/sdk-reference/guide/07-contributions.html:150` and `08-ui.html:78`.
- Test: `jasper-sdk-testkit/src/testFixtures/java/dev/jasper/sdk/testing/AnchorContractTest.java` (both runtimes) and
  `jasper-app/src/test/java/dev/jasper/app/plugins/HostedAnchorTest.java`.

**Interfaces** (`@since` the version above):
- **`UiAnchor UiView.anchorAt(String customId, java.awt.Rectangle area)`:** an anchor on `area` of a mounted, visible,
  enabled Custom node with registered content, in the content component's coordinates; an empty area is a point.
  - A popover opens below the area, or above it when there is no room, as `fit` places one today. A dialog may take
    the anchor as its owner.
  - Hiding, disabling, removing or moving the node closes it, and so does resizing the content: a page laid out again
    has moved what was there.
  - It throws `IllegalArgumentException` for an id naming no such node and for a negative width or height.
- **`JComponent KitView.customTarget(String id)`:** a live Custom node's registered component, refusing anything else
  as `anchorTarget` does.
- **`HostedAnchor`** gains an area (null for control anchors), checks the content's size against its size at minting,
  and leaves the picker-expanded property alone. **`HostedPopover.placement`** adds the area to the target's screen
  location.
- **`List<Rectangle> FakePluginHost.popoverAreas(String pluginId)`:** the areas of that plugin's shown popovers opened
  on an area anchor. `FakeKitView.anchorAt` checks what the host checks.

- [ ] **Step 1: Write the failing tests:**
  - **`AnchorContractTest`:**
    - `anAreaOfCustomContentIssuesAnAnchorForAPopover`: `anchorAt(id, new Rectangle(10, 20, 30, 8))` on a registered
      Custom node, and a popover that shows while the anchor is open;
    - `anAreaAnchorClosesWhenItsCustomNodeHidesOrLeaves`: an update hiding the node, and one dropping it, each close
      the anchor and its popover;
    - `onlyARegisteredCustomNodeIssuesAnAreaAnchor`: a Button id, an unknown id, unregistered content and a negative
      width each throw.
  - **`HostedAnchorTest`:** `anAreaAnchorClosesWhenItsContentIsResized`, and `anAreaPopoverFitsBelowTheArea` (content
    at screen (100, 100), area (10, 20, 30, 8) and a 200×100 popover give (110, 128)).

- [ ] **Step 2: Run** `./gradlew :jasper-sdk-testkit:test :jasper-app:test --tests '*Anchor*'`. Expected: FAIL.

- [ ] **Step 3: Implement** the member, the kit seam, the host side, the fake, the version bump and the guides.

- [ ] **Step 4: Run** `./gradlew sdkReference`, then `./gradlew check`, then
  `./gradlew -p docs/examples/external-plugin -PjasperSdkDir="$PWD/build/sdk-release" check pluginZip`. Expected:
  PASS. Commit the regenerated `docs/sdk-reference.html`.

- [ ] **Step 5: Commit** "SDK: an anchor on an area of custom content".

### Task 2: The Vault API additions

**Files:**
- Modify, under `plugins/vault/src/main/java/dev/jasper/vault/`: `api/CredentialPickRequest.java`,
  `api/VaultApi.java`, `service/VaultService.java` (`ConsumerApi`), `ui/AnchoredPicker.java` and `VaultPlugin.java`.
- Modify: `plugins/vault/src/main/resources/plugin.toml` (`version`).
- Modify: `docs/plugin-apis.md`'s Credential Vault API section (the new members and `search`, the default-bodies
  paragraph, the Vault version, the Browser among the bundled consumers) and `docs/credential-vault.md`'s "Other
  plugins and grants" (logins another plugin saves, and their grant).
- Test: `plugins/vault/src/test/java/dev/jasper/vault/service/VaultServiceTest.java`, `AnchoredPickTest.java` and
  `plugins/vault/src/test/java/dev/jasper/vault/VaultPluginTest.java`.

**Interfaces:**
- **`record CredentialPickRequest(UiAnchor anchor, Optional<UUID> current, boolean allowClear, String search)`:**
  `search` is the chooser's starting search, stripped, never null. The three-argument constructor stays and gives
  `""`, as `CredentialDescriptor`'s shorter one does. `AnchoredPicker` starts `query` at `search`.
- **`default CompletableFuture<Optional<CredentialDescriptor>> VaultApi.saveLogin(WindowHandle owner, String name,
  String username, char[] password)`:**
  - unlocks over `owner` when needed, then in one `lock.transact` adds an `Account` with `Auth.Password` and a saved
    `Grant` for this plugin;
  - when this plugin already has a saved grant for a password login named exactly `name`, it replaces that login's
    username and password instead, keeping its id, grants and created time, and zeroes the old `Auth` *(call: as
    iTerm2 saves under the account name, a changed password replaces the old one)*;
  - copies `password`, which the caller still zeroes;
  - answers the descriptor, or empty when unlocking is cancelled or there is no vault (which posts "No vault yet");
    fails with `IllegalStateException` ("Could not save the login") when the write fails; throws
    `IllegalArgumentException` for a blank name or a null username or password;
  - the default body fails with `UnsupportedOperationException`.
- **`default Optional<Credential> VaultApi.credentialIfGranted(UUID id)`:** the secret, never prompting, while the
  vault is unlocked and this plugin holds a saved grant for `id`. One-time grants are neither used nor spent. Empty
  otherwise, as in the default body.
- **`default void VaultApi.openManager(WindowHandle owner)`:** the Credential Vault manager over `owner`, creating or
  unlocking first as Open Vault... does; failures are the Vault's notifications. The default body does nothing.
- **`void VaultService.managerOpener(Consumer<WindowHandle> open)`,** installed before the API is published, as
  `anchoredPicker` is; `VaultPlugin` passes `window -> open(window, Optional.empty())`.

- [ ] **Step 1: Write the failing tests:**
  - **`AnchoredPickTest`:** `aRequestsSearchStartsTheChooserFiltered`: with search `example.com`, the menu's query is
    `example.com`, it lists only matching credentials, and it has no None row.
  - **`VaultServiceTest`:**
    - `saveLoginStoresAPasswordLoginWithASavedGrant`: its kind, name and username; `grants()` holds
      `Grant(consumer, id)`; `credentialIfGranted(id)` reads the password; the caller's array is untouched;
    - `saveLoginReplacesTheGrantedLoginOfTheSameName` (same id, new password, one login of that name) and
      `saveLoginLeavesAnUngrantedLoginOfTheSameNameAlone` (a second login);
    - `saveLoginUnlocksFirstAndAnswersEmptyWhenCancelled`, `saveLoginWithNoVaultPostsNoVaultYet` and
      `saveLoginFailsWhenTheWriteFails`;
    - `credentialIfGrantedNeedsASavedGrant`: after a pick it answers empty and the next `credential` still uses the
      one-time grant without a prompt; locked, unknown and ungranted ids answer empty; no grant prompt ever shows.
  - **`VaultPluginTest`:** `openManagerOpensTheManagerOverTheOwner`: unlocked, the manager shows; with no vault, the
    create flow runs, as for Open Vault....

- [ ] **Step 2: Run** `./gradlew :jasper-plugin-vault:test`. Expected: FAIL.

- [ ] **Step 3: Implement,** and bump the Vault's version.

- [ ] **Step 4: Run** `./gradlew sdkReference`, then `./gradlew :jasper-plugin-vault:check :jasper-plugin-remote:test
  :jasper-plugin-fileserver:test :jasper-sdk:check`; the Remote and File Server fakes compile unchanged. Expected:
  PASS. Commit `docs/sdk-reference.html`, which carries the Vault's API.

- [ ] **Step 5: Commit** "Vault: the picker's starting search, saveLogin, credentialIfGranted and openManager".

### Task 3: The Browser's Vault bridge, login names and Manage Saved Passwords

**Files:**
- Create, under `plugins/browser/src/main/java/dev/jasper/browser/logins/`: `package-info.java`, `LoginName.java` and
  `VaultLogins.java`.
- Modify: `plugins/browser/src/main/resources/plugin.toml`: `requires = [{ id = "dev.jasper.vault", version =
  ">=<Task 2's version>", optional = true }]`, and the Browser's `version`.
- Modify: `plugins/browser/build.gradle.kts` (`compileOnly` and `testImplementation` of `:jasper-plugin-vault`, as
  Remote's) and `gradle/plugin-architecture.gradle.kts:13` (`declaredImports`: `":jasper-plugin-browser" to
  listOf("dev.jasper.vault.api")`).
- Modify: AGENTS.md's architecture paragraph and `docs/sdk-architecture.md`: the Browser optionally consumes the Vault
  API.
- Modify: `BrowserPlugin.java` (the lookup), `item/BrowserItems.java` (`Optional<VaultLogins> vault()`) and plan 6's
  Settings page (the button).
- Test, under `plugins/browser/src/test/java/dev/jasper/browser/`: `logins/LoginNameTest.java`,
  `logins/VaultLoginsTest.java`, `logins/FakeVault.java`, `VaultBoundaryTest.java`, `BrowserManifestTest.java`,
  `BrowserPluginTest.java` and plan 6's Settings page test.

**Interfaces:**
- **`static String LoginName.of(URI origin, String realm)`:** `scheme://host[:port] (realm)`, with the scheme and host
  lowercased, IPv6 hosts bracketed, the port only when it isn't the scheme's default, no user info, path, query or
  fragment, and no ` (realm)` for a blank realm.
- **`final class VaultLogins`** (UI thread):
  - `static Optional<VaultLogins> find(PluginContext context)` catches `NoClassDefFoundError`;
  - `CompletableFuture<Boolean> pick(WindowHandle owner, UiAnchor anchor, String search, BiConsumer<String, char[]>
    use)`: `pick(owner, new CredentialPickRequest(anchor, Optional.empty(), false, search))`, then
    `credential(owner, id)`; `use` gets the username and password, each empty when there is none, before the
    credential closes; true when a login was used;
  - `boolean useGranted(String name, BiConsumer<String, char[]> use)`: while unlocked, the first `ACCOUNT_PASSWORD` in
    `credentials()` named exactly `name` that `credentialIfGranted` reads; false when none;
  - `CompletableFuture<Boolean> save(WindowHandle owner, String name, String username, char[] password)`:
    `saveLogin`, true when saved, false when empty, failing as it fails;
  - `boolean unlocked()` and `void openManager(WindowHandle owner)`;
  - a `NoSuchMethodError` from an older Vault answers nothing chosen, nothing filled, not saved.
- **`BrowserPlugin.start`** looks the Vault up once and hands the `Optional` to `BrowserItems` and the Settings page.
- **Manage Saved Passwords:** a button after Manage Site Permissions in Privacy & Data, only with the Vault, that calls
  `openManager` for the window Settings shows over. If Settings' modality blocks the manager (Task 8 shows it), open it
  the way plan 6 opens the permissions page from Settings, and record which in the banner.

- [ ] **Step 1: Write the failing tests:**
  - **`LoginNameTest`:** `https://Example.COM:443/x?q#f` with `Corp` gives `https://example.com (Corp)`;
    `http://example.com:8080` with a blank realm gives `http://example.com:8080`; `https://user:pw@example.com/` gives
    `https://example.com`; `https://[::1]:8443/` gives `https://[::1]:8443`; a realm with quotes is kept.
  - **`VaultLoginsTest`** (`FakeVault`): `pick` passes the anchor and search, `use` sees `u` and `pw` and the
    credential is closed after, and `Cancelled` answers false; `useGranted` skips a same-named SSH key and a login the
    fake refuses, and answers false while locked; `save` and `openManager` forward; a fake throwing
    `NoSuchMethodError` answers false.
  - **`VaultBoundaryTest`**, on `ChromiumBoundaryTest`'s scan: no class but `logins/VaultLogins` refers to
    `dev/jasper/vault/`.
  - **`BrowserManifestTest`:** the optional Vault requirement.
  - **`BrowserPluginTest`:** `withoutTheVaultNoLoginsAppear`: with no Vault provider, the plugin starts and Settings
    has no Manage Saved Passwords. **The Settings page test:** with the Vault, the button calls `openManager` with the
    window.

- [ ] **Step 2: Run** `./gradlew :jasper-plugin-browser:test`. Expected: FAIL.

- [ ] **Step 3: Implement.**

- [ ] **Step 4: Run** `./gradlew :jasper-plugin-browser:check verifyPluginArchitecture`. Expected: PASS.

- [ ] **Step 5: Commit** "Browser: the optional Vault, login names and Manage Saved Passwords".

### Task 4: The field buttons

**Files:**
- Create: `plugins/browser/src/main/java/dev/jasper/browser/page/PageQuery.java`,
  `plugins/browser/src/main/java/dev/jasper/browser/logins/FieldButtons.java` and
  `plugins/browser/src/main/resources/dev/jasper/browser/logins/login-fields.js`.
- Modify: `page/Page.java` (`handleLogins`, `loginQuery`), `page/PageEvents.java` (running the script),
  `links/OpenerBridge.java` (`login.` ops, the deferred reply, `Queries.onQueryCanceled`) and `item/BrowserItem.java`
  (the wiring, when `BrowserItems.vault()` is present).
- Test: `page/PageQueryTest.java`, `logins/FieldButtonsTest.java`, `logins/LoginFieldsScriptTest.java` and
  `links/OpenerBridgeTest.java`.

**Interfaces:**
- **`final class PageQuery`,** a page's request answered later:
  - `PageQuery(Page page, Optional<String> origin, Map<String, Object> request, BooleanSupplier activation,
    Supplier<Optional<String>> currentOrigin, BiConsumer<Integer, String> reply)`, where `origin` is the asking
    document's, empty when opaque;
  - `page()`, `origin()`, `request()` and `boolean consumeActivation()`;
  - `boolean answer(String json)` replies `(0, json)` once, but replies `GONE` and sends nothing when `currentOrigin`
    no longer equals `origin`; `boolean fail(int code, String why)`; the codes `MALFORMED` 1, `REFUSED` 2, `GONE` 3
    and `BUSY` 5, tied to the bridge's by a test;
  - `Subscription onCancelled(Runnable listener)` and `void cancelled()`, after which nothing is sent.
- **`void Page.handleLogins(String script, Consumer<PageQuery> handler)`** (UI thread, once): from then on each
  main-frame load start of an `http:` or `https:` document runs `script` in the main frame, as 4b runs its scripts.
  `void Page.loginQuery(PageQuery)` hands the query to the handler, or fails it `REFUSED`, "This page has no password
  manager.".
- **The bridge:** a `login.` op from the top frame becomes a `PageQuery` passed to `page.loginQuery` on the UI thread.
  The page's waiting count settles when the handler returns, and the reply comes once, later. A sub-frame gets
  `SUB_FRAME`. `Queries.onQueryCanceled` calls `cancelled()`.
- **`login-fields.js`:**
  - runs once per document (`Symbol.for("dev.jasper.browser.logins")`) and keeps its own reference to
    `jasperBridgeQuery`, taken when it runs;
  - on `focusin` (capture), an enabled, editable, visible `input[type=password]` shows 🔑 (`"\u{1F511}"`), and an
    input whose `autocomplete` tokens include `username` shows 🎫 (`"\u{1F3AB}"`), iTerm2's rule;
  - the button sits inside the field's right edge, in a closed shadow root with `position: fixed`, titled and
    labelled "Open Password Manager"; it follows scrolling and resizing, its `mousedown` keeps focus in the field, and
    it goes on `focusout` elsewhere;
  - a click sends `{op: "login.pick", field: "password" | "username", rect}`, `rect` being the field's
    `getBoundingClientRect()`;
  - a reply's values go through the native value setter, followed by bubbling `input` and `change`. From a username
    field it fills the username, then focuses and fills the first `input[type=password]` after it, in its form when it
    has one. It fills only the elements it holds, while connected, and skips empty values;
  - it logs no values, and it is ASCII, with the emoji as escapes.
- **`final class FieldButtons`:**
  - `static final String SCRIPT` (the resource) and `PICK = "login.pick"`;
  - `record FieldRect(double x, double y, double width, double height)`, and `static Optional<FieldRect> rect(Object
    value)`, which takes finite numbers with width and height at least 0;
  - `static Optional<Rectangle> area(FieldRect css, double zoom, Dimension view)`: scaled by `zoom` (the item's
    percent / 100), clipped to `view`, empty when nothing is left;
  - `FieldButtons(VaultLogins vault)` and `void query(PageQuery q, WindowHandle window, Function<FieldRect,
    Optional<UiAnchor>> anchor)`:
    - `MALFORMED` for an unknown op or field or a bad `rect`; `REFUSED` for an origin that isn't http(s), and for no
      activation, which is checked and spent before the picker opens ("The password manager opens only after a click
      in the page."); `BUSY` while this page's picker is open; `REFUSED` when `anchor` gives nothing (the field is
      off the page);
    - otherwise `vault.pick(window, anchor, host of origin, use)`, answering a password field `{"password": …}` and a
      username field `{"username": …, "password": …}`, empty members left out, and `{}` when nothing was chosen,
      built with `BridgeJson.quote`;
    - a cancelled query cancels the pick, which closes the picker.
- **`BrowserItem`:** `page.handleLogins(FieldButtons.SCRIPT, q -> buttons.query(q, tab.window(), css ->
  FieldButtons.area(css, zoom, pageView.getSize()).map(r -> view.anchorAt(PAGE, r))))`.

- [ ] **Step 1: Write the failing tests:**
  - **`PageQueryTest`:** it answers once; an origin that changed answers `GONE` and sends no JSON; nothing is sent after
    `cancelled()`; the codes equal `OpenerBridge`'s.
  - **`FieldButtonsTest`** (a recording query and `FakeVault`):
    - `aClickOpensThePickerAtTheFieldWithTheHost`: at zoom 1.25, the CSS rect (8, 16, 200, 24) anchors at
      (10, 20, 250, 30), searched for `example.com`;
    - `aPickWithoutTheUsersClickIsRefused` (no picker), `aSecondPickWhileOneIsOpenIsRefused` (`BUSY`),
      `aFileOrOpaqueOriginIsRefused`, and `malformedRequestsAreRefused` (an unknown field, no rect, `NaN`, a negative
      width);
    - `aPasswordFieldGetsThePasswordOnly`, `aUsernameFieldGetsBoth` and `aCancelledPickAnswersEmpty` (`{}`);
    - `aChoiceAfterTheDocumentChangedOriginFillsNothing` (`GONE`, the credential closed) and
      `aCancelledQueryClosesThePicker` (the pick future cancelled).
  - **`LoginFieldsScriptTest`:** the resource is ASCII and holds `\u{1F511}`, `\u{1F3AB}`, "Open Password Manager",
    `login.pick` and `autocomplete`; it is injected into http and https documents only, never `file:`,
    `jasper-about:`, `about:blank` or `data:`.
  - **`OpenerBridgeTest`:** a top-frame `login.pick` reaches the page's handler and is answered once, later; it holds
    none of the page's 8 waiting places; a sub-frame gets `SUB_FRAME`; a page without a handler gets `REFUSED`.

- [ ] **Step 2: Run** the tests. Expected: FAIL.

- [ ] **Step 3: Implement.**

- [ ] **Step 4: Run** `./gradlew :jasper-plugin-browser:check`. Expected: PASS.

- [ ] **Step 5: Commit** "Browser: the Vault's 🔑 and 🎫 buttons on page fields".

### Task 5: The HTTP sign-in dialog

**Files:**
- Create: `plugins/browser/src/main/java/dev/jasper/browser/page/SignIn.java`, and under
  `plugins/browser/src/main/java/dev/jasper/browser/logins/`: `Challenge.java`, `SignInDialog.java` and
  `HttpSignIns.java`.
- Modify: `page/Page.java` (`onSignIn`, `signIn`, `mainFrameRequested`, `topAddress`, `onMainFrameResponse`,
  `mainFrameResponded`), `links/PageNavigation.java` (`Links.getAuthCredentials`, and noting main-frame requests),
  `links/OpenerBridge.java` (`Responses`: redirects and the final status), `item/BrowserItem.java` and
  `item/BrowserItems.java` (one `HttpSignIns`, made in `BrowserPlugin`).
- Test: `page/SignInTest.java`, `logins/ChallengeTest.java`, `logins/SignInDialogTest.java` and
  `logins/HttpSignInsTest.java`.

**Interfaces:**
- **`final class SignIn`:** `SignIn(String originUrl, boolean proxy, String host, int port, String realm, String
  scheme, String topAddress, BiConsumer<String, String> proceed, Runnable cancel)`, with a getter per value
  (`scheme()` lowercased); `boolean proceed(String user, String password)` and `boolean cancel()` answer once and log
  a failure inside; `boolean answered()`.
- **`Links.getAuthCredentials`** answers false for a browser that isn't a `PageBrowser` with a page. Otherwise it makes
  a `SignIn` over `callback::Continue` and `callback::cancel` with `page.topAddress()`, posts `page.signIn(it)` to the
  UI thread, and returns true. `Page.signIn` cancels on a closed page or one with no handler.
- **`Page.topAddress()`:** the main frame's latest navigation request's address, after redirects, else the page's
  address; `Links.getResourceRequestHandler` and `Responses.onResourceRedirect` call `mainFrameRequested` (any thread).
  `Responses.onResourceResponse` posts `page.mainFrameResponded(url, status)`, which tells the `onMainFrameResponse`
  listeners on the UI thread.
- **`record Challenge(String origin, String host, String realm, String scheme, boolean proxy, boolean framed)`:**
  - `static Challenge of(SignIn s)`, framed when `originUrl`'s origin differs from `topAddress`'s (spec section 21);
  - `boolean basic()`; `boolean offersVault(boolean devNull, boolean vault)`: Basic, not a proxy, outside /dev/null,
    with the Vault; `boolean remembers(boolean devNull, boolean vault)`: `offersVault` and not framed;
    `String loginName()`: `LoginName.of(origin, realm)`;
  - a proxy challenge gets the plain dialog naming the proxy's host *(call)*.
- **`SignInDialog`:**
  - `static void show(DialogPlace place, Challenge c, boolean offersVault, boolean remembers, Optional<VaultLogins>
    vault, Consumer<Outcome> answered)`: a modal overlay over the item's tab, shown only while the item is active and
    waiting until then, as `ScriptDialog` is; `static String heading(String host, String realm)`;
  - User name (TEXT), Password (Secret INPUT) and, only when `remembers`, the toggle "Remember this password", off;
  - buttons, left to right: Password Manager (only when `offersVault`), Cancel, OK (primary). Enter in either field is
    OK; Escape and closing are Cancel;
  - Password Manager calls `vault.pick(place.owner().window(), view.anchor(<its button>), c.host(), …)`: a choice
    closes the dialog as `Picked`, and a cancelled picker keeps it;
  - `sealed interface Outcome` with `Ok(String user, char[] password, boolean remember)`, `Picked(String user, char[]
    password)` and `Cancelled()`; it answers once, the item's close is `Cancelled`, and the secret is cleared on close.
- **`final class HttpSignIns`** (UI thread): `HttpSignIns(Optional<VaultLogins> vault, NotificationGroup notices)`;
  - `void challenged(Page page, DialogPlace place, boolean devNull, SignIn signIn)`: one dialog per page at a time, the
    rest waiting in order, and past `WAITING_LIMIT = 8` waiting cancelled at once. `Ok` and `Picked` call `proceed`
    (making the password a `String` there and zeroing the array); `Cancelled` calls `cancel`;
  - `void responded(Page page, String url, int status)` (Task 6), and `void closed(Page page)`, which cancels every
    waiting challenge.
- **`BrowserItem`** wires `page.onSignIn(s -> signIns.challenged(page, place, devNull(), s))`, the response listener,
  and `closed` on close.
- **Without the Vault plugin** the dialog still shows, without the Vault controls, so pages can still sign in *(call)*.

- [ ] **Step 1: Write the failing tests:**
  - **`SignInTest`:** `proceed` then `cancel` reaches the callback once; a throwing callback is contained.
  - **`ChallengeTest`:** `https://a.example` under top `https://a.example/x` is not framed, and under
    `https://b.example/` it is; `Basic` and `basic` both count as Basic; Digest, NTLM, a proxy, /dev/null and no Vault
    each turn off `offersVault`; a framed Basic challenge `offersVault` but doesn't `remembers`.
  - **`SignInDialogTest`** (FakeUi): the heading "The website “intranet.example” requires a user name and password
    for “Corp”.", and without the realm part when there is none; the labels; each control present only by its flag;
    OK gives `Ok` with the typed values and the toggle; Cancel, Escape and closing give `Cancelled`; Password Manager
    opens the picker on its button with the host, a choice gives `Picked`, and a cancelled picker keeps the dialog;
    `closingTheItemAnswersCancelledOnce`.
  - **`HttpSignInsTest`:** `aChallengeShowsTheDialogAndOkProceedsOnce`, `aSecondChallengeWaitsForTheFirst`,
    `theNinthWaitingChallengeIsCancelled` and `closingTheItemCancelsEveryChallengeOnce`.

- [ ] **Step 2: Run** the tests. Expected: FAIL.

- [ ] **Step 3: Implement.**

- [ ] **Step 4: Run** `./gradlew :jasper-plugin-browser:check`. Expected: PASS.

- [ ] **Step 5: Commit** "Browser: Jasper's dialog for HTTP sign-ins".

### Task 6: Remember this password, and the silent fill

**Files:**
- Modify: `plugins/browser/src/main/java/dev/jasper/browser/logins/HttpSignIns.java`.
- Test: `logins/HttpSignInsTest.java`.

**Interfaces:**
- **The silent fill:** when `remembers` holds, `vault.unlocked()`, and no silent fill was tried for this page and login
  name since the page's last main-frame response, `vault.useGranted(loginName, …)` proceeds with no dialog and marks
  the name tried. Otherwise, or when it answers false, the dialog shows. In /dev/null mode there is never a silent fill
  *(call: iTerm2 offers its password store nowhere in /dev/null)*.
- **A pending save:** `Ok` with `remember` keeps one per page (the origin, the login name, the user, a copy of the
  password). A new challenge on the page replaces it and `closed(page)` drops it, each zeroing the password.
- **`responded(page, url, status)`:** a 2xx from the pending save's origin saves it with
  `vault.save(place.owner().window(), name, user, password)` and zeroes the password when the save completes. Any other
  response drops it; redirects never arrive here. Either way the page's tried names are cleared.
- **Outcomes:** a failed save posts `notices.error("Could not save the password for " + host, reason)`; an empty answer
  (unlocking cancelled, no vault) posts nothing more. Nothing is saved from a framed challenge, a /dev/null item,
  Digest, NTLM or a proxy, and no web form is ever saved.

- [ ] **Step 1: Write the failing tests** in `HttpSignInsTest`:
  - `rememberedThenTwoHundredSavesTheLogin`: `save("https://intranet.example (Corp)", "u", "pw")`, the array zeroed
    after; `aFourOhOneOrAnotherOriginSavesNothing`; `aPendingSaveIsZeroedWhenTheItemCloses`;
    `aNewChallengeReplacesThePendingSave`;
  - `aFailedSavePostsCouldNotSave` ("Could not save the password for intranet.example") and
    `aCancelledUnlockPostsNothing`;
  - `aGrantedLoginFillsWithoutTheDialog`; `aSecondChallengeAfterASilentFillShowsTheDialog`, after which a 200 lets the
    next challenge fill silently again; `aLockedVaultShowsTheDialog`;
  - `devNullFramedDigestAndProxyNeverFillSilentlyOrRemember`.

- [ ] **Step 2: Run** the tests. Expected: FAIL.

- [ ] **Step 3: Implement.**

- [ ] **Step 4: Run** `./gradlew :jasper-plugin-browser:check`. Expected: PASS.

- [ ] **Step 5: Commit** "Browser: Remember this password, and remembered sign-ins without the dialog".

### Task 7: Docs and the check

**Files:**
- Modify: `docs/browser.md`: a Logins section (the field buttons and how they fill, in the top frame only *(call)*;
  the sign-in dialog and when it offers Password Manager and Remember; login names and the Browser's saved grant; the
  silent fill and when the dialog shows instead; frames told apart by origin only; /dev/null mode; what is missing
  without the Vault plugin), and Manage Saved Passwords in Settings.
- Modify: `docs/credential-vault.md`: the Browser among the plugins with pickers, and the logins it saves.
- Modify: `docs/STATUS.md`, with the plan's entry and its calls for the user (field buttons in the top frame only; the
  dialog without the Vault plugin; proxy challenges; no silent fill in /dev/null; `saveLogin` replacing the granted
  same-named login), and this plan's status banner.

- [ ] **Step 1: Write the docs,** documenting only what the code does (AGENTS.md's documentation rules).

- [ ] **Step 2: Run the source hygiene script,** then `./gradlew sdkReference`, `./gradlew check` and
  `./gradlew -p docs/examples/external-plugin -PjasperSdkDir="$PWD/build/sdk-release" check pluginZip`. Expected:
  PASS. Record the counts from the XML.

- [ ] **Step 3: Commit** "Docs: the Browser's logins".

### Task 8: The user's GUI check, with their OK (pending; never blocks the merge)

**Files:**
- Modify: `docs/browser-verification.md`: a section for plan 8.

- [ ] **Step 1: Check on macOS** (`:jasper-app:run` from the worktree, with the user's OK), recording each line pass
  or fail:
  1. **Buttons:** on a sign-in page, the password field shows 🔑 and an `autocomplete=username` field shows 🎫, each
     titled "Open Password Manager".
  2. **Filling:** 🔑 opens the Vault's picker under the field, searched for the host, and the choice fills the
     password; 🎫 fills the username, then focuses and fills the password; a locked Vault asks to unlock first.
  3. **Leaving:** navigating away with the picker open fills nothing.
  4. **Basic:** `https://httpbin.org/basic-auth/user/passwd` shows "The website “httpbin.org” requires a user name and
     password for “Fake Realm”."; OK signs in, Cancel shows the 401 page, and Password Manager signs in with a chosen
     login.
  5. **Remember:** ticked, the Vault then holds "https://httpbin.org (Fake Realm)"; after a relaunch with the Vault
     unlocked the page signs in with no dialog; locked, the dialog shows; a remembered wrong password shows the dialog
     on the second try, with no loop.
  6. **Plain dialogs:** `https://httpbin.org/digest-auth/auth/user/passwd` and a /dev/null item get the dialog without
     Vault controls; a local page framing the Basic URL gets it without Remember.
  7. **Manage Saved Passwords** opens the Vault manager, usable from Settings.
  8. **No Vault:** with the Vault plugin off, there are no buttons and no Manage Saved Passwords, and the dialog has
     neither Vault control.

- [ ] **Step 2: Commit** "Browser: the logins' GUI check on macOS". Windows and Linux are recorded as not run until the
  user has those hosts.

## What follows

- **Plan 9:** the Chromium Markdown preview in the Editor, through `dev.jasper.browser.api`.
- **Plan 10:** the documentation pass of spec section 23.
