# Browser 8: logins — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or
> superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Execution status (2026-11-11): Tasks 1-6 executed and reviewed (Task 1 as 1a and 1b, Task 4 as 4a and 4b and Task 5 as
5a and 5b, ruling L-F17), and Task 7, the docs; the final whole-branch review and its fix wave come next; Task 8, the
user's GUI check on macOS, is pending** (branch `claude/browser-logins`, worktree
`.claude/worktrees/browser-4b-4c-e6deab`, cut from local `main` `c01d2feec`, which has plans 4a, 4b, 4c, 9, 5, 6 and 7
merged; the plan `58962695b`, brought in from `5c42e4a82`, then commits `a2b5c118a` to `322bd5793`, and the docs; not
merged or pushed; see `docs/STATUS.md`). Executed with per-task subagents and reviews, each task's fix rounds until a
re-review found every finding addressed. The pre-flight scan's findings F1-F30 were adopted as rulings (L0, cited
L-F*n*) except where L1-L3 decide otherwise, and the controller's further rulings are L4-L32; plan 4a's R0-R48 and the
banners of 4b, 4c, 9, 5, 6 and 7 bind. The user's answers (2026-11-09): nothing to download, no licence check and no
third-party file, and no change to the plan's calls: `saveLogin` replaces the granted login of the same name, a proxy
challenge gets the plain dialog, the sign-in dialog shows without the Vault, no silent fill in /dev/null, and field
buttons only in the top frame. F5, F11 and F22 refine three of those calls without reversing them (L3), and F7 adds SDK
API (L2). The task reviews recorded these deviations from the plan's text, each with its ruling:
- **Versions:** `JasperSdk.VERSION` 0.31.0 to 0.32.0, every bundled range `>=0.32.0, <0.33` (Task 1a, L-F17), the next
  minor after the branch's. New SDK members, each `@since 0.32.0`: `ContentArea` and `UiView.anchorAt(String,
  ContentArea)` (L1, in place of the plan's `java.awt.Rectangle`, so `PluginPresentationCheck` gains no exception and no
  self-test fixture), and `SettingsPanelHost.closeThen(Runnable)` (L2, F7, which the plan did not have); the testkit's
  `FakePluginHost.popoverAreas` (a `List<ContentArea>`, not the plan's `List<Rectangle>`), `answerDiscard` and
  `discardQuestions`. Credential Vault 0.8.0 to 0.9.0, its exported API without `@since`; Remote's and File Server's
  optional `requires` stay `>=0.2`. **The Browser stays 0.2.0** (L-F17, plan 5's H6), not the plan's next minor:
  `dev.jasper.browser.api` is unchanged, and its optional `requires` of the Vault, `>=0.9.0`, asks for no capability.
- **The splits** (L-F17, L-F18): Task 1 as 1a, the bump, and 1b, the features; Task 4 as 4a, the page's login queries,
  and 4b, the field buttons; Task 5 as 5a, sign-ins as plain types, and 5b, the dialog and `HttpSignIns`. Each half has
  its own brief and commits. The page's login hooks live in the package-private `page.PageLoginHooks` and the item's
  wiring in `item.PageLoginsHost` (L-F18), each with its `migration.csv` row.
- **Task 1a** (`a2b5c118a` "SDK: 0.32.0"): `371a94680`'s files, the guide pages, STATUS's current-state line and the
  generated reference, 39 files; `PresentationSelectionTest`'s pinned 0.31.0 moved too; the dated "SDK 0.31.0" lines of
  `docs/plugin-authoring.md` stay.
- **Task 1b** (`0894c27f3` "SDK 0.32.0: an anchor on an area of custom content, and closing Settings first"; fix round
  `61a584675` "SDK 0.32.0: the anchor's and closeThen's review fixes"): `ContentArea` refuses a negative size and an
  edge past the int range (L4); `anchorAt` and `closeThen` are default methods that throw
  `UnsupportedOperationException`, as plan 7's `addEntries` is; an area must lie inside its content when its anchor is
  made, refused with `IllegalArgumentException` in both runtimes (L5); `KitView.customTarget` beside `anchorTarget`;
  `HostedAnchor` closes an area anchor at its content's first real resize through a `ComponentListener`, a contract case
  too; `HostedPopover.onScreen` feeds `fit`, and for an area only a press inside it is consumed and no picker property
  is set (L-F16); the `closeThen` contract case in `PluginContractTest`, with four `ContractHarness` members and the
  app's harness driving the real `SettingsDialog`; `closeThen` enforces the UI thread, and `SettingsDialog.closeThen`
  runs the action a turn later, contained, and not for a stopped plugin (L5, L7); the Vault's, Editor's and Remote's
  test `RecordingView`s delegate `anchorAt` (L4).
- **Task 2** (`d6836bbab` "Vault: the picker's starting search, saveLogin, credentialIfGranted and openManager"; fix
  rounds `93e226465` "Vault: the login API's review fixes" and `ee2b4cfaf` "Vault: an unlock prompt that cannot show is
  forgotten"): a null `search` is refused, not read as `""`; `saveLogin` answers empty at once for a null or closed
  owner and for a failed unlock, fails with `IllegalStateException("Could not save the login")` for a write failure or
  a throw starting it, replaces only the last password login of exactly that name whose saved grants are all the
  saver's (L-F11), and runs the Vault's changed hook after its commit (L9), with the test seams `saveCopies` and
  `replacedPasswords`; `credentialIfGranted(null)` answers empty; `openManager` records its owner, as `ensureUnlocked`
  does, and does nothing for a null or closed one (L9); the three new members throw `IllegalStateException` off the UI
  thread (L9); the changed hook is contained where the service takes it, and an unlock prompt that cannot show is
  forgotten, its waiters answering false (L10, L11); `docs/plugin-apis.md`'s stale 0.7.0 became 0.9.0.
- **Task 3** (`42b019702` "Browser: the optional Vault, login names and Manage Saved Passwords"; fix round `9b600a31f`
  "Browser: the Vault bridge's review fixes"): the Settings keyword "passwords" only with the Vault; both
  `BrowserItems` constructors gained the Vault, with 25 test callers; a package-private request-maker seam on
  `VaultLogins` for the older-Vault test; `pick` answers false for a Vault failure, logged with the exception alone, and
  `save` fails as `saveLogin` fails; `LoginName.of` throws `NullPointerException` for a null realm; `pick` uses only an
  `ACCOUNT_PASSWORD`, fetching and closing any other kind unused (L12, L14), where the plan said "a credential … each
  empty when there is none"; `VaultBoundaryTest` with a prefix and a dotted-string fixture.
- **Task 4a** (`6c50db78e` "Browser: the page's login queries"; fix round `de4b36437` "Browser: the login queries'
  review fixes"): the codes' tie test in `OpenerBridgeTest`; an opaque origin matches none, so its query can only be
  told GONE; a query leaves the bridge through its own cancel at the page's close; `answer` takes an
  `OpenerBridge.Pending` holder, registered on Chromium's thread before the post, so an early cancel opens no picker
  (L16), and the map, now `cancellable()`, holds every admitted query until it is answered; the at-once test request
  refuses `login.pick`; `Page.loginQuery` refuses another page's query; one stopped supplier for both routers,
  `PrivacyQueries.install` taking it in place of `Chromium` (L15); cancel listeners contained, and an origin that cannot
  be read answers GONE (L17).
- **Task 4b** (`07b9d7c98` "Browser: the Vault's 🔑 and 🎫 buttons on page fields"; fix rounds `819c4d68c` "Browser and
  Vault: the field buttons' review fixes" and `c06d07097` "Browser and Vault: the press oracle and wording"):
  `FieldButtons.area` gives a `ContentArea` (L1, L6) and `rect` reads L-F2's flat request into a `FieldRect` of `int`s;
  a writer seam; `closePicker()`; one `FieldButtons` per page; `live()` is checked before the click is spent (L18), and
  `PageQuery`'s reply is a `BiPredicate` that answers false once Chromium has stopped; L6's scroll close is the script
  cancelling its own query through the captured `jasperBridgeCancel`, not a new op, and its resize close the area
  anchor's own; "a navigation" is a main-frame load start, a committed address change, same-document ones included, or
  Jasper's `onNavigate`; captured built-ins beyond L-F13's list; styles through CSSOM `setProperty`; a fill needs the
  field still editable; a `use` that throws answers `{}`; L20: `LastPress` gains its point on the canvas, `query` takes
  the area and the anchor as two functions and spends the click last, as the picker opens, a press outside the area
  spending it (L21); the Vault's `AnchoredPicker` ignores a choice for 500 ms through `VaultPlugin.pickerClock`, its
  unused three-argument constructor removed, and Remote's `VaultTestAccess` and the app's `StagedVaultHost` step past
  it; `PageLoginsHostTest` and `LoginsTestAccess` beyond the plan's files.
- **Task 5a** (`81876a884` "Browser: HTTP sign-ins reach the page"; fix round `c0bc64927` "Browser: the sign-ins' top
  address follows each commit"): `Challenge(origin, host, port, realm, scheme, proxy, framed, topLevel)`, with `port`
  and `topLevel` beyond the plan's record, the realm cleaned and the scheme lowercased in its constructor, framed when
  either origin is missing, a realm cut at 99 code points and "…", and no realm in `toString`; L13 in `offersVault`;
  `SignIn.originOnly()`; every null text from Chromium read as `""`; the main-frame request noted in
  `PageNavigation.requests`, whose test row became "read once, for its navigation"; plan 6's F43 guard on every
  `CefAuthCallback` answer; `Links.getAuthCredentials` through a static `signIn(...)` with a `ui` seam; L23 and L24:
  `PageEvents.addressChanged` sets the top address on Chromium's thread before its post, `topLevel()` is false for a
  proxy, and `Page.mainFrameRequestEnded` falls back to the commit before for a load error or a download of the top
  address, by compare-and-set; F12's DEBUG line spends its one shot only while DEBUG is on.
- **Task 5b** (`a7c34cc91` "Browser: Jasper's dialog for HTTP sign-ins"; fix rounds `5b7b3252b` "Browser: the sign-in
  dialog's review fixes" and `e96d6bea1` "Browser: the flood bypass ends on Cancel"): `SignInDialog` in `dialogs`, not
  `logins` (L-F4), with L-F4's signature; its `show` returns a `Subscription` and starts a turn later, as
  `ScriptDialog.ask` does; `PageModal` gains `viewMade` and `otherEvents`; `ScriptDialog.AfterPrompts` is
  package-private, shared rather than copied; the title "Sign In" (L26), with the heading as its first BODY line;
  `SignInDialog.TITLE` public; the flood guard's clock seam; `BrowserItems` keeps its two constructors, which make their
  own `HttpSignIns`; `HttpSignIns` hears `page.onClose` too; L-F30 compares the raw realm; an `Ok` for a page whose
  sign-ins ended is cancelled; `SignInDialog`'s `migration.csv` row is `standard-ui,5,5`, as `ScriptDialog`'s; the code
  came before its tests, RED taken against BASE (a process deviation); L27's `usersNavigation`, from
  `HttpSignIns.navigating` on `page.onNavigate`, `HttpSignIns.close()` from `BrowserPlugin.stop` before Chromium closes,
  and the test seam `shownAnswer` returning an answerer; L28's ends of the bypass, a Cancel and a main-frame load
  failure.
- **Task 6** (`f59b26b0b` "Browser: Remember this password, and remembered sign-ins without the dialog"; fix rounds
  `171e595d6` "Browser: the silent fill re-arms only on its own origin's page" and `322bd5793` "Browser: a silent fill
  re-arms only after its own answer"): the silent fill runs as the challenge's turn comes in the page's queue, not as it
  arrives; a 407 keeps the tried names as a 401 does; the pending save keeps the OK answer's own array; any new
  challenge drops the pending save, one the waiting limit refuses included; `remembers` is checked again before a save
  is kept; three existing rows mark their logins ungranted; L30's tried names keep their origin, and a load start drops
  the pending save; round 2 (L31), against the plan's text at the `Page.topAddress()` interface:
  `Responses.onResourceRedirect` calls the new `Page.mainFrameRedirected` rather than `mainFrameRequested`,
  `Page.navigation()` counts main-frame navigation requests, `SignIn` carries its navigation number, `page` and `links`
  change beyond the brief's one file, and a load failure ends every tried name's answer.
- **Task 7** ("Docs: the Browser's logins"), the docs: the Browser guide (Logins, /dev/null mode, Clearing data, the
  HTTP proxy's 407 and Settings), the Credential Vault guide, plugin APIs' `credential`, `pick` and `importSshKeys` rows
  (L11), the README's, Plugins' and the docs index's Browser descriptions (D8's precedent), AGENTS.md's Verified JCEF
  behaviour (L-F12: what javap confirmed), this banner and STATUS. The SDK guides, the testkit README and
  `docs/plugin-authoring.md` came with Task 1b, plugin APIs and the Vault guide with Task 2, and AGENTS.md's
  architecture sentence and `docs/sdk-architecture.md` with Task 3. The last full check, at `322bd5793` before the docs
  (the controller's, 10m 40s), gave **16,220 tests, 0 failures, 0 errors, 105 skipped** (1,425 classes in the 16 build
  folders, without the example plugin's: jasper-app 5,373 with 92 skipped, Editor 5,392 with 4 skipped, Browser 1,857
  with 1 skipped, jasper-ui 1,021, Remote 660, jasper-terminal 512 with 7 skipped, jasper-sdk-testkit 334, File Server
  303, Vault 275 with 1 skipped, jasper-buddy 176, jasper-sdk 139): plan 7's 15,911 and plan 8's 309. The full checks
  after each task: 15,911 (Task 1a, indicative), 15,934 and 15,942 (1b), 15,973 and 15,990 (2), 16,024 and 16,028 (3),
  16,056 and 16,060 (4a), 16,097, 16,105 and 16,106 (4b), 16,144 and 16,149 (5a), 16,178 and 16,189 (5b), 16,207 and
  16,220 (6), each with 0 failures, 0 errors and 105 skipped; the example external plugin's `check pluginZip` against
  `build/sdk-release` passed after each; D49's `FilterListTest` flake did not show. The full check and the
  external-plugin check on the docs commits are the controller's. No GUI was launched.

The controller's rulings, one line each:
- L0: every ruling the pre-flight scan proposed (F1-F30) is adopted and cited as L-F*n*, except where L1-L3 decide
  otherwise.
- L1: F1: `UiView.anchorAt` takes the SDK record `ContentArea(x, y, width, height)`, not `java.awt.Rectangle`, so the
  SDK names no more AWT and `PluginPresentationCheck` gains no exception.
- L2: F7 is built in Task 1b: `SettingsPanelHost.closeThen(Runnable)` closes Settings, asking about unsaved changes,
  before Manage Saved Passwords opens the Vault's manager, which Settings' modality would block.
- L3: F5 (Remember and the silent fill only for the top-level navigation's own challenge), F11 (`saveLogin` replaces
  only a login whose sole grant is the saver's) and F22 (the proxy heading names HOST:PORT) refine the user's calls
  without reversing them; F17 keeps the Browser at 0.2.0; `getAuthCredentials` is built for either thread (F12), which
  Task 8 records.
- L4: Task 1b's deviations accepted; `ContentArea` refuses an edge past the int range, `closeThen`'s action is
  contained in both runtimes, and the plugins' test `RecordingView`s pass `anchorAt` on.
- L5: an area anchor must lie inside its content when it is made; `closeThen` enforces the UI thread and runs its
  action a turn later, once Settings' modal loop has unwound; the Javadoc says content that scrolls or zooms closes its
  own anchor; one check order in both runtimes.
- L6: the field picker closes on a page scroll, a Jasper zoom change, a navigation or a resize, as the host cannot see
  Chromium's scrolling.
- L7: Task 1b round 1's choices accepted: the deferral in `SettingsDialog`, a stopped plugin's action skipped, and
  containment shown through the contract and the failure records.
- L8: Task 2's choices accepted; a lock during the save's write answers empty though the login may be written (a known
  gap).
- L9: `saveLogin` runs the Vault's changed hook after its commit; a throw from `requestUnlock` zeroes the copy and
  rethrows; `openManager` does nothing for a null or closed owner; the three new members enforce the UI thread.
- L10: the changed hook is contained at both its callers, and `requestUnlock` forgets a prompt it could not show, its
  waiters answering false.
- L11: an unshowable prompt reads as a cancel at its callers, which plugin APIs' `credential`, `pick` and
  `importSshKeys` rows say (Task 7); two pre-existing Vault minors are deferred (below).
- L12: the Browser's pick uses only a password login, still fetching and closing any other, so its one-time grant is
  spent; a kind filter in the Vault's picker is a follow-up.
- L13: a challenge whose origin `LoginName.of` refuses, such as a host with an underscore, gets the plain dialog and
  logs nothing about the host.
- L14: a key-and-password account fills nothing either, as `useGranted` and `saveLogin` take only `ACCOUNT_PASSWORD`;
  `BrowserPluginTest`'s older stderr warnings go to the final wave.
- L15: no reply to a login query once Chromium has stopped (plan 6's F43), through one stopped supplier for both
  routers.
- L16: a cancel from Chromium before the UI thread's hand-off is seen there, so no picker opens and closes at once.
- L17: Task 4a's fix round (L15, L16, contained cancel listeners, a throwing origin read answering GONE, the at-once
  request's refusal pinned); `login-fields.js` does nothing in an opaque document.
- L18: Task 4a's last minors go to Task 4b: `PageQuery.answer` answers false once Chromium has stopped, the bridge's
  Javadoc, and the handler checks `live()` before the picker opens.
- L19: Task 4b's concerns accepted: the scroll close through CEF's cancel (a Task 8 line), a same-document address
  change closing the picker, and a button off its field under a root `transform` or `filter` (a known gap).
- L20: a pick needs its activating press inside the area it names, and every plugin's anchored picker ignores a choice
  for 500 ms after it shows; a search anchored on the login name is a Vault follow-up.
- L21: a press outside the area spends the click, so a page learns at most one bit per press (a known gap);
  `VaultApi.pick`'s Javadoc names the 500 ms; the picker's keyboard path is a Task 8 line.
- L22: the stale `consumeActivation` Javadoc, a refused anchor spending the click and long Javadoc lines go to the final
  wave.
- L23: each main-frame commit clears the noted request, so a restored page of another origin inherits no top-level
  standing.
- L24: L23 on Chromium's thread, before the commit's post; `topLevel()` is false for a proxy; a main-frame load error
  and a download of the top address fall back to the commit before.
- L25: Task 5a accepted; a same-document commit during a pending navigation and a refused redirect only withhold (known
  gaps); `onResourceLoadComplete` with the request's identity is a follow-up.
- L26: the flood guard counts a queued sign-in as quick, as `PermissionPrompt`'s queue does (a known gap); after a
  Cancel each waiting challenge of the same space asks on its own; the dialog is titled "Sign In", as Chrome's is.
- L27: a top-level challenge of a navigation Jasper started for the user shows past a tripped flood guard until the next
  load start; `BrowserPlugin.stop` ends every sign-in before Chromium closes; a framed Basic challenge has Password
  Manager without Remember, as the plan says.
- L28: a Cancel of a dialog admitted past the trip, or a main-frame load failure, ends that navigation's bypass.
- L29: Task 5b accepted; matching the load failure's URL and `SignInDialog`'s overstated sentence go to the final wave;
  the waiting limit and a proxy's challenge of the user's navigation are known gaps.
- L30: a tried name is re-armed only by a main-frame response of its own origin that is neither a 401 nor a 407, at the
  commit of that origin that follows; a load start drops the pending save.
- L31: the stricter reading of L30 holds, and a name whose fill sent a password re-arms only on the answer to the fill's
  own navigation, its redirects included; a fill whose navigation is replaced leaves the name tried while the tab lasts.
- L32: Task 6 accepted; the top address's write order, test and Javadoc nits and L29's URL match for the answer's reset
  go to the final wave; the other costs are known gaps.

Known gaps:
- L-F5 and L30: a page that navigates to an address and fetches that same address, or its origin's root, gets that
  navigation's top-level standing, Remember and the silent fill, though the password still goes only to that origin;
  when CEF names only an origin, every same-origin challenge does (Task 8's F12 line records which form arrives).
- L-F22 and L29: each tab a proxy challenges asks on its own, as CEF resupplies no other pending challenge, and a
  proxy's challenge of the user's navigation gets no flood bypass, as `topLevel()` is false for a proxy.
- L-F23: an HTTP sign-in lasts until Jasper quits, and Clear Website Data does not end it, as JCEF has no
  `ClearHttpAuthCredentials`.
- The field buttons: none in frames (the user's call), inside shadow roots (L20) or in opaque documents (L17); a button
  off its field on pages with a root `transform` or `filter` (L19); another item's zoom of the same host can misplace
  the picker, which is then refused rather than opened elsewhere when the press falls outside (L20); without an emoji
  font on Linux the button shows a box (L-F27).
- L20 and L21: the Vault's starting search is a substring, so other sites' logins can be listed, and whether the
  picker's starting search is selected on focus is unchecked; a page learns one bit per press, whether the press lay
  inside a rect the page names.
- Secrets: the reply's password copies (`new String`, `StrictJson`'s builder, the reply text) and JCEF's
  `Continue(String, String)` cannot be zeroed.
- L8: a lock during the save's background write answers empty, though the login may be in the vault at its next unlock.
- L24 and L25: a main-frame request that never commits and reports no error (a 204 or 205, `window.stop()`, a redirect
  Chromium refuses) keeps its top-level standing until the next request or commit; a same-document commit of the shown
  page during a pending navigation withholds that navigation's Remember.
- L26, L28, L29 and L32: a page that trips the dialog limit, as with three realms at once, loses its third sign-in, its
  alerts and its permission prompts until it loads again; a page with 8 challenges waiting can cancel the user's
  navigation's at arrival; a link click or Reload in a tripped page gets no bypass; an earlier navigation's late
  `ERR_ABORTED` can end a quick second navigation's bypass, and a fill's answer, until the final wave's URL match; after
  a Cancel each waiting challenge of the same space asks on its own, at most two.
- L30, L31 and L32: the old document can drop the user's pending save with any challenge; after a silent fill that
  Stop or a quick navigation interrupts, the dialog shows for that name in that tab until it closes; a quick new
  request can cost a fill that worked its re-arm, as the commit's navigation number is read on the UI thread; two
  top-level challenges of one name with no response between fill the first and show the dialog for the second; a
  prerender activation, if this CEF prerenders through the page's handler, is unchecked.
- L-F9: two realms that differ only in control characters share one login name.

Deferred minors:
- Pre-existing in the Vault, unfixed in plan 8 (L11): `VaultManagerDialog` keeps a settled `unlockPrompt` when
  `panel.showUnlock` throws, so `changed()` skips `passiveUnlock()` until the next prompt replaces it; and
  `UnlockPrompt.finish` runs its dismiss listeners before it completes the waiters, so a throwing listener leaves them
  incomplete (`unlock` is already null, so the next request still makes a fresh prompt).
- For the final wave (L14, L22, L24, L25, L29, L32): `PageQuery`'s stale "a refused request spends nothing" and a
  refused anchor spending the click; Javadoc lines past 120 characters in `FieldButtons` and `VaultApi`; the existing
  stderr warnings of `BrowserPluginTest` and of `OpenerBridgeTest`'s
  `aPageTheBridgeCannotReachDoesNotStopTheOtherCloseListeners`; `PageDownloads`' Javadoc on a download of the top
  address; the load-failure clear matched by the failed URL, for the bypass and for the answer's reset, and
  `SignInDialog`'s sentence on the user's navigation; `PageLoginHooks` setting the top address before it counts the
  navigation; `HttpSignInsTest`'s 121-character line; `PageSignInsTest`'s redirect rows, which call
  `mainFrameRequested`; and Javadoc nits in `Page`, `PageNavigation` and `HttpSignIns`.
- Follow-ups: a filter by kind in the Vault's picker (L12), a search anchored on the login name (L20), and
  `onResourceLoadComplete` with the request's identity (L25).

Not run:
- **Task 8**, the user's GUI check on macOS, with their OK for each batch: pending, F12, F14, F6, F13 and F7 first,
  then the plan's eight lines, each with the task reviews' lines, and the known gaps' lines.
- **Benchmarks:** none new; the plugin's start finds the Vault and makes `HttpSignIns`, and loads no Chromium class
  (`StartLoadsNoChromiumTest`).
- **Windows and Linux** (no hosts): NTLM or Negotiate on an intranet host, Kerberos with a ticket, the emoji font, and
  Settings' modality (L-F27); and **packaging with AOT**, whose training needs the user's OK.
- **No JavaScript runs in a test** (D3's rule carries): `login-fields.js` is checked as text, with `node --check` and
  a stub harness in `.superpowers/sdd/probes/p8-4b/`; no test starts Chromium.

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

- **Plan 10:** the documentation pass of spec section 23.
