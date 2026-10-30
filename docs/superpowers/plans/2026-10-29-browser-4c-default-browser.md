# Browser 4c: registering Jasper as a browser — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or
> superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let the user make Jasper the system's default web browser on macOS, Windows and Linux, as iTerm2 allows.
- **URLs handed over from outside** open in Jasper's browser with no prompt: by the system, by `open -a Jasper <url>`,
  by `jasper <url>`, or as launch arguments.
- **When Jasper is the default,** clicked links open in Jasper's browser without asking.
- **When the Browser plugin is off,** a notification offers to turn it on, instead of looping back to Jasper.

**Architecture:**
- **App code** does the OS side:
  - Info.plist URL types and an open-URI handler on macOS;
  - launch arguments and the single-instance handoff carry URLs;
  - the installers register Jasper (Windows `RegisteredApplications`, Linux `x-scheme-handler`);
  - a default-browser check per platform.
- **The SDK** tells plugins whether Jasper is the default.
- **The Browser plugin's** `LinkDecision` gains spec section 9's rule 2.

**Tech Stack:** Java 25, the macOS Objective-C bridge (`libjasper-macos.dylib`), FFM for the Windows registry (as
`platform/WindowsUserPath.java`), jpackage resources (WiX 3 on Windows, deb and rpm on Linux), and JUnit 5.

**Spec:** [docs/superpowers/specs/2026-10-26-jasper-browser-plugin-design.md](../specs/2026-10-26-jasper-browser-plugin-design.md),
section 9 ("The decision", rule 2; "URLs from outside"; "Registering as a browser"), plus the deviation that plan 3b
moved here: the plugin-off notification and the default-browser check
([plan 3b](2026-10-27-browser-3b-routing-amendment.md), Task 8).

## Global Constraints

- **Branch:** from `main` once plan 4a has merged, or else from `claude/browser-core`, in its own worktree.
- **SDK version:** the SDK addition takes the next minor after the branch's `JasperSdk.VERSION`, with every bundled
  manifest range moving with it.
- **Facts from `main`** (gathered 2026-10-28, through git at `a6f4259de`; check the lines again before editing):
  - **Routing:** `JasperApplication.openExternalAddress` (around :914-918) uses `systemBrowser` before the plugins
    start, else `PluginRuntime.openExternalLink`. `WebBrowsers.open` returns false with no browser. Nothing checks the
    default browser.
  - **macOS open handler:** the handler is installed at `JasperApplication.java:484-490`, behind
    `supports(APP_OPEN_FILE)` and before the first window, and removed at shutdown (around :2587). There is no URI
    handler.
  - **Info.plist:** `gradle/packaging.gradle`'s macOS `packageApp` branch runs `plutil -replace` for
    `CFBundleDocumentTypes` (around :322-327, `macDocumentTypes` at :35-37). `verifyPackage` checks the plist (around
    :675-695).
  - **Launch arguments:** `bootstrap/AppArguments.java:96-119` resolves every non-option argument as a path.
    `residency/LaunchRequest.java` is protocol 2 with `Intent {RAISE, OPEN_PATHS}` and absolute paths only. The owner
    side is `JasperApplication.handedOff` (around :1316-1333).
  - **The `jasper` command** already sends `jasper <url>` over the command socket as `EXTERNAL`.
  - **Windows:**
    - `packageInstaller` runs jpackage `--type exe` with `--resource-dir` holding only `main.wxs`, extracted and patched
      by `packaging/windows/cygwin/CygwinInstallerTemplate.java` (`withHook`);
    - there are no file associations;
    - `Verify-Installer.ps1` doesn't read the `Registry` table;
    - `platform/WindowsUserPath.java` reads `HKCU` strings through FFM (`RegGetValueW`).
  - **Linux:** jpackage generates `lib/jasper-workstation-Jasper.desktop` from its own template, with no `MimeType`.
    The maintainer scripts run `xdg-desktop-menu install`, and `verifyUnpackedPackage` checks `Exec` and `Categories`.
  - **Plugin state:** `PluginRuntime.setEnabled(id, enabled, done)` takes effect from the next launch, and
    `RestartFlow` offers the restart.
  - **Notifications:** the model is `JasperApplication.handedOff`'s `notificationCenter.post(NotificationRequest.of(...)
    .withAction(...))`.
- **No GUI from agents.** Making Jasper the default browser changes the user's system settings, so only the user does
  it (Task 7). Never run `./gradlew --stop`.
- **Platforms:** all three, on x64 and aarch64. Windows and Linux packaging runs where those hosts or CI exist; record
  "not run" otherwise.
- **Commits** end with:

  ```
  🤖 Generated with Claude Code at The Home Depot

  Co-Authored-By: Claude <noreply@anthropic.com>
  ```

## Review Focus

1. **Jasper is the default and the Browser plugin is off, or hasn't started yet.** A URL from outside must never be
   handed back to the system, which would loop. It waits for the plugins to start, then goes to the browser or to the
   notification. Task 3 pins this.
2. **Launch arguments mixing paths and URLs,** and `file:` URLs from outside: paths and `file:` keep opening as files,
   and only http(s) goes to the browser. Task 4 pins this.
3. **A second launch with URLs while the first is starting.** The handoff carries them, nothing is lost, and nothing
   opens twice. Task 4 pins this.
4. **The default-browser check when its tool is missing or slow** (`xdg-settings` absent, the registry key missing).
   It answers "not the default" quickly, and never blocks the EDT. Task 1 pins this.
5. **Upgrading and uninstalling on Windows and Linux** leaves no dangling association. Uninstall removes the keys and
   the desktop entry's MIME types. Tasks 5 and 6 pin this.

---

### Task 1: Is Jasper the default browser?

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/platform/DefaultBrowser.java`.
- Modify: `gradle/mac-native.gradle` and its Objective-C source: add `MacLaunchServices_defaultBrowserBundleId(const
  char* scheme)`, which returns the bundle id of `NSWorkspace URLForApplicationToOpenURL:` for `<scheme>://example.com`.
- Modify: the bridge symbol list checked by `verifyPackage` (`packaging.gradle`, around :656-659).
- Modify: `platform/WindowsUserPath.java`: a package-private `readString(String subKey, String name)` for any `HKCU`
  key, if its reader isn't already general.
- Test: `DefaultBrowserTest.java`, and the bridge's native test.

**Interfaces:**
- **`final class DefaultBrowser`:**
  - `DefaultBrowser(Executor worker, Clock clock)`;
  - `boolean isJasper()` answers at once from the last result, false before the first;
  - `void refresh()` re-checks on `worker`, at most once per 30 s, and is called at start-up and when a Jasper window
    activates.
- **Per platform**, `https` counts:
  - **macOS:** the bridge's bundle id equals `dev.jasper.app`;
  - **Windows:** `HKCU\Software\Microsoft\Windows\Shell\Associations\UrlAssociations\https\UserChoice` value `ProgId`
    equals `JasperURL`;
  - **Linux:** `xdg-settings get default-web-browser` (a 2 s timeout) equals the package's desktop file id,
    `jasper-workstation-Jasper.desktop`.
  - **Any error or timeout** gives false.
- **`static boolean DefaultBrowser.parse*(...)`** are the pure comparisons the test checks for each platform.

- [ ] **Step 1: Write the failing tests:**
  - the three comparisons;
  - the 30 s throttle, with a fake clock;
  - false before the first answer;
  - a missing tool or key giving false;
  - a timeout giving false, without blocking the caller.

- [ ] **Step 2: Run** the tests. Expected: FAIL.

- [ ] **Step 3: Implement.**

- [ ] **Step 4: Run** `./gradlew :jasper-app:test --tests '*DefaultBrowserTest'`, plus the mac bridge's native tests
  through `check` on the Mac. Expected: PASS.

- [ ] **Step 5: Commit** "App: whether Jasper is the system's default browser".

### Task 2: The SDK's answer, and the plugin's rule 2

**Files:**
- Modify: `jasper-sdk/src/main/java/dev/jasper/sdk/ui/Platform.java`: add `isDefaultBrowser()`.
- Modify: `jasper-app/.../plugins/HostedContext.java`, the fakes and the contract cases.
- Modify: `plugins/browser/.../routing/LinkDecision.java` and `BrowserRole.java`.
- Test: `PluginContractTest`, `LinkDecisionTest` and `BrowserRoleTest`.

**Interfaces:**
- **`boolean Platform.isDefaultBrowser()`** (`@since` the version above): true when Jasper is the system's default
  handler for `https`, as last checked. UI thread; never blocks.
- **`LinkDecision.decide(WebLink link, BrowserSettings settings, boolean jasperIsDefault)`:** a `CLICK` web link is
  `OPEN` whenever `jasperIsDefault` holds, before the setting is consulted (spec section 9, rule 2). `file:` links and
  `EXTERNAL` are unchanged.

- [ ] **Step 1: Write the failing tests:**
  - the decision table, extended with `jasperIsDefault`;
  - the contract case `isDefaultBrowserFollowsTheHost`;
  - `BrowserRoleTest`: as the default, no prompt shows even with `ask`.

- [ ] **Step 2: Run** the tests. Expected: FAIL.

- [ ] **Step 3: Implement,** bump the SDK, run `sdkReference`.

- [ ] **Step 4: Run** `./gradlew check` and the external-plugin check. Expected: PASS.

- [ ] **Step 5: Commit** "SDK: Platform.isDefaultBrowser; the Browser opens links without asking when it is the
  default".

### Task 3: URLs from outside never loop

**Files:**
- Modify: `jasper-app/.../application/JasperApplication.java`:
  - `openExternalAddress`;
  - `openInSystemBrowser`;
  - a held queue for URLs that arrive before the plugins start;
  - the notification.
- Test: `JasperApplicationPluginsTest` (new cases) and `ApplicationCommandsTest`.

**Interfaces:**
- **Before the plugins start,** `openExternalAddress` holds the URL in arrival order, with at most 32 held, and drops
  further ones with a log line. It delivers them once the plugin runtime is up.
- **No browser provided** (the plugin off, or it declined):
  - **when `DefaultBrowser.isJasper()`:** post the notification and open nothing;
  - **otherwise:** the system browser, as today.
- **`openInSystemBrowser`, when Jasper is the default:** it never calls `Desktop.browse` for http(s). It sends the
  address to the provided browser, or else posts the notification. This also covers "Use Default Browser" and
  `Platform.browse`.
- **The notification,** in the GENERAL group:
  - title "Web links can’t open in Jasper";
  - content "The Browser plugin is off. Turn it on to open web links in Jasper, or choose another default browser in
    your system settings.";
  - actions **Turn On Browser**, which calls `runtime.setEnabled("dev.jasper.browser", true, …)` and then the restart
    offer, and **Copy Link**.

- [ ] **Step 1: Write the failing tests:**
  - a URL before the plugins start is delivered after they start;
  - the 33rd held URL is dropped;
  - plugin off plus Jasper default posts the notification and never calls the desktop;
  - plugin off without Jasper default calls the desktop;
  - `Platform.browse` as the default reaches the browser.

- [ ] **Step 2: Run** the tests. Expected: FAIL.

- [ ] **Step 3: Implement.**

- [ ] **Step 4: Run** `./gradlew :jasper-app:test`. Expected: PASS.

- [ ] **Step 5: Commit** "App: URLs from outside wait for the browser, and never loop back to the system".

### Task 4: macOS URL types, the open-URI handler and launch URLs

**Files:**
- Modify: `gradle/packaging.gradle`:
  - a `macUrlTypes` constant beside `macDocumentTypes`, and its input property;
  - `plutil -replace CFBundleURLTypes -json` beside the document types;
  - a `verifyPackage` check that mirrors the document-types one.
- Modify: `JasperApplication.java`:
  - `Desktop.setOpenURIHandler` beside the open-file handler (`APP_OPEN_URI`): http(s) goes to `openExternalAddress`
    in the most recently active window, and `file:` URIs to the file path;
  - remove it at shutdown, beside the open-file handler's removal.
- Modify: `bootstrap/AppArguments.java` (`urls()`), `bootstrap/ApplicationBootstrap.java` (`openFirstWindow`),
  `residency/LaunchRequest.java` (protocol 3, `List<URI> urls`), `residency/SingleInstance.java` and
  `JasperApplication.handedOff`.
- Test: `AppArgumentsTest`, `LaunchRequestTest`, `SingleInstanceTest`, the handoff cases, and the packaging fixtures
  in `gradle/packaging-support-test.gradle`.

**Interfaces:**
- **`macUrlTypes`** is `[{"CFBundleURLName":"Web site URL","CFBundleURLSchemes":["http","https"],"CFBundleTypeRole":"Viewer"}]`.
- **`AppArguments.urls()`** holds the absolute http and https arguments, which are no longer resolved as paths.
  Everything else stays a path.
- **`LaunchRequest`** gains `urls` (absolute http(s) only, at most 32) at protocol 3. A protocol-2 request still
  decodes with no URLs. Jasper is an MVP, so this is the protocol's only change. `Intent.OPEN_PATHS` covers a request
  with URLs.
- **A first launch** opens its URLs after the plugins start, as `EXTERNAL`. **A second launch** hands them over, and
  the owner opens them in its most recently active window.

- [ ] **Step 1: Write the failing tests:**
  - `jasper-app https://example.com notes.txt` gives one URL and one path;
  - `file:///x.html` stays a path;
  - a protocol-3 request round-trips, and a protocol-2 one decodes;
  - the owner opens handed-over URLs once;
  - the packaging fixture finds `CFBundleURLTypes` equal to `macUrlTypes`.

- [ ] **Step 2: Run** the tests. Expected: FAIL.

- [ ] **Step 3: Implement.**

- [ ] **Step 4: Run** `./gradlew check`, then `./gradlew :jasper-app:packageApp :jasper-app:verifyPackage` on the Mac
  (headless, no `-Pjasper.aot`). Expected: PASS.

- [ ] **Step 5: Commit** "macOS and launch arguments: Jasper takes web URLs from outside".

### Task 5: Windows registration

**Files:**
- Modify: `packaging/windows/cygwin/CygwinInstallerTemplate.java`, or a sibling transform it calls: add a `Component`
  of `RegistryKey`/`RegistryValue` elements under `HKCU`, removed on uninstall.
- Modify: `packaging/windows/Verify-Installer.ps1`: read the MSI's `Registry` table and require the rows.
- Test: the template's own test (the transform's output XML), and `Verify-Installer.ps1` where a Windows host runs it.

**Interfaces** (the keys, the per-user MSI's `[INSTALLDIR]`):
- `HKCU\Software\RegisteredApplications`, value `Jasper` = `Software\Jasper\Capabilities`.
- `HKCU\Software\Jasper\Capabilities`: `ApplicationName` = `Jasper`, `ApplicationDescription` = `Jasper's built-in web
  browser`.
- `HKCU\Software\Jasper\Capabilities\URLAssociations`: `http` = `JasperURL` and `https` = `JasperURL`.
- `HKCU\Software\Classes\JasperURL`:
  - `(Default)` = `Jasper URL`;
  - `DefaultIcon` = `"[INSTALLDIR]Jasper.exe",0`;
  - `shell\open\command` = `"[INSTALLDIR]Jasper.exe" "%1"`.
- **Not registered:** the portable ZIP and the per-user dev install. `docs/browser.md` says so *(call)*.

- [ ] **Step 1: Write the failing test.** The transformed `main.wxs` holds exactly these keys, inside a component that
  the uninstall removes.

- [ ] **Step 2: Run** `./gradlew verifyPackagingSupport` (the template test runs there, or in its own task). Expected:
  FAIL.

- [ ] **Step 3: Implement.**

- [ ] **Step 4: Run** the test. Expected: PASS. On a Windows host, `packageInstaller` and `Verify-Installer.ps1`;
  otherwise record not run.

- [ ] **Step 5: Commit** "Windows: the installer registers Jasper as a web browser".

### Task 6: Linux registration

**Files:**
- Modify: `gradle/packaging.gradle` (`writeInstallScripts`, around :946-973): add a `template.desktop` override in the
  resource directory, with `MimeType=x-scheme-handler/http;x-scheme-handler/https;` and `Exec=… %u`, keeping
  jpackage's other fields.
- Modify: the maintainer scripts: after `xdg-desktop-menu install`, run `update-desktop-database -q <applications
  dir>` when it exists. Uninstall runs it again.
- Modify: `verifyUnpackedPackage` (around :997-1001) and `verifyInstallScripts` (around :976-982).
- Test: the packaging fixtures.

**Interfaces:**
- **The desktop file** keeps jpackage's `Name`, `Icon`, `Categories` and `Terminal=false`, and gains `MimeType`, with
  `%u` on `Exec`.
- **Packages don't make Jasper the default.** The user chooses it with their desktop's settings or
  `xdg-settings set default-web-browser jasper-workstation-Jasper.desktop` (`docs/browser.md`).

- [ ] **Step 1: Write the failing tests:** the fixture's desktop file holds the `MimeType` and `%u`, and the scripts
  call `update-desktop-database` behind an existence check.

- [ ] **Step 2: Run** `./gradlew verifyPackagingSupport`. Expected: FAIL.

- [ ] **Step 3: Implement.**

- [ ] **Step 4: Run** the test. Expected: PASS. Run `packageDeb` and `packageRpm` in the Linux container or on CI where
  available; otherwise record not run.

- [ ] **Step 5: Commit** "Linux: the desktop entry offers Jasper as a web browser".

### Task 7: Docs, the check and the user's checks

**Files:**
- Modify:
  - `docs/browser.md` ("Making Jasper your default browser", for each platform, and what happens when the Browser
    plugin is off);
  - `docs/command-line.md` (launch URLs);
  - `docs/packaging.md` (URL types, the registry keys, the desktop entry);
  - `docs/STATUS.md`, and this plan's banner;
  - `docs/browser-verification.md`, with a 4c section.

- [ ] **Step 1: Write the docs,** documenting only what the code does.

- [ ] **Step 2: Run the source hygiene script,** `./gradlew sdkReference`, `./gradlew check` and the external-plugin
  check. Expected: PASS. Record the counts.

- [ ] **Step 3: The user's check on macOS,** with their OK. It changes their default browser, so they make the change
  and change it back.
  1. Package with `packageApp` (headless), install the built `Jasper.app`, and choose Jasper in System Settings ›
     Desktop & Dock › Default web browser.
  2. A link clicked in another app opens in Jasper's browser.
  3. `open -a Jasper https://example.com` opens a tab.
  4. A terminal Cmd-click opens without asking.
  5. With the Browser plugin turned off and Jasper relaunched, a link from outside shows "Web links can’t open in
     Jasper". Turn On Browser and the restart bring it back.
  6. Choose the previous default browser again.

- [ ] **Step 4: Commit** "Browser: registering as the default browser, checked on macOS". Windows and Linux are
  recorded as not run until the user has those hosts.
