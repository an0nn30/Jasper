# Browser 3b: link routing for iTerm2 parity — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or
> superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Execution status (2026-10-27): executed** (commits `f89fcee81` to `60599de5d`, then "STATUS: browser plan 3b's
check"); see `docs/STATUS.md`. The task reviews recorded these deviations from the plan's text:
- **Branch:** executed on `claude/browser-routing-3b-b72db8`, cut from `claude/browser-link-routing` at `665d4bce5`, not
  on `claude/browser-link-routing` as the Global Constraints say.
- **Spec, step 1 of Task 8:** section 23 puts the plugin-off notification for addresses from outside in this amendment.
  It needs the "is Jasper the system's default browser" check, which belongs with registering as a browser, so both move
  to plan 4. Until then `jasper <address>` with no browser provided opens the system browser.
- **Task 1:** also made the minimal app edits that keep `jasper-app` compiling and run the new contract cases on the app
  runtime (`WebBrowsers.open` builds the `WebLink`); the app's routing returns false, so the system browser opens, for
  an address `WebLink` refuses (an http(s) URI with an authority but no host, such as `http://my_host/`); `WebLink`'s
  scheme check is case-insensitive and refuses an opaque or relative `file:` URI; and `MarkdownNavigationTest` and
  `TunnelsPluginTest` were updated for the new `WebBrowser.open`.
- **Task 2:** `PluginRuntime.openLink` and `openExternalLink` keep plan 3's `Consumer<URI> desktop` fallback; the final
  review changed `openLocalWebFile` to take the file's `file:` URI (built off the UI thread) instead of a `Path`.
  `openUrlIsNoLongerACommand` asserts that `open-url` gets the same answer as any unknown first word ("Jasper's plugins
  are not running" without plugins), not "no such file or folder", which applies only when a file named `open-url`
  exists. `aWebAddressGivenToTheCommandIsExternal` is in `JasperApplicationPluginsTest`, not `ApplicationCommandsTest`
  (only that class runs a real provided browser). `jasper <url>` keeps the calling pane's window.
- **Task 3:** `LinkHit` is in `dev.jasper.terminal.internal.text`, not the package of `BufferQueries` (the view never
  depends on `internal.emulation`), and `TerminalAccess.linkAt` changed too. The filter also ends a held OSC at U+009C,
  as JediTerm does, and follows an OSC 8 that names a target with an empty `link-target`, so no target outlives its
  link; the brief's first filter test therefore expects that trailing clear. The engine re-checks a `link-target` a
  program sends itself.
- **Task 4:** the `supported` set gains only `view.TerminalLink` (the check strips nested names, so
  `view.TerminalLink$Gesture` could never match); `MouseRouting.linkGesture` and `MouseControllerLinkGestureTest` run
  every platform's rule on every host (the view tests for Linux and Windows skip on the Mac). Docs where "target" meant
  a link's URI now say "address" or "URI".
- **Task 5:** the placement mapping is asserted in `JasperApplicationPluginsTest` (`FileLinkClickTest`'s package cannot
  reach `JasperApplication`); `WindowContent.paneClicks` already selects a pressed pane's tab in a shown window, and the
  opener's own `tabFocused` call pins presses that bypass it; `TerminalPane` needed no edit.
- **Task 6:** `PluginMenu.addForWebAddress` returns a `Subscription` (not `void`) and also works in submenus of the
  terminal section; the selection is trimmed of all leading and trailing whitespace (`strip()`), so a selected line's
  trailing line break is accepted; the testkit gains `FakePluginHost.contextMenu(paneId)` and
  `chooseFromContextMenu(paneId, actionId)` (and the contract harness the same), documented as the terminal menu's model
  lines; `menu()` lists `web-address:<id>`. Step 4's combined command cannot run as one Gradle invocation
  (`sdkReference` and `verifySdkReference` have no ordering), so they run separately.
- **Task 7:** terminal file clicks get their own `WindowContent.onOpenClickedFile` (Open File… shared `onOpenFile` with
  them); a file named only by its extension (`.html`) has no extension; the browser cases are in
  `JasperApplicationPluginsTest`; `JasperApplication.fileOpener` is a package-private test seam like `workspaceWriter`.
- **Order:** the final whole-branch review ran over Tasks 1-7 before Task 8, so the check covers its fixes.
- **Final review:** its fixes (`60599de5d`) build the file URI off the UI thread, say "address" where an OSC 8 link's
  address was called its target, read the selection for the web-address items lazily, and regenerate
  `docs/sdk-reference.html`.
- **Task 8:** `./gradlew check --continue` (Python 3.13, JBR 25.0.4.1 b635.75) ran 13,538 tests with 0 failures, 0
  errors and 32 skipped (the baseline was 13,474 and 30; the two extra skips are Linux-only view tests, which skip on
  the Mac). Every task passed except `:jasper-app:verifyLauncherImage`, which fails on this JBR on every branch and
  which plan 2's `claude/browser-jcef-runtime-0c197e` fixes.

**Goal:** Bring plan 3's link routing in line with the approved iTerm2-parity design. It adds origins `CLICK` and
`EXTERNAL`, split clicks, OSC 8 targets, right-click items for a selected URL, and local web files offered to the
browser. It also removes `jasper open-url` and the `PROGRAM` origin.

**Architecture:** Plan 3 built a web browser *role*: a plugin registers a `WebBrowser`, and Jasper asks it before the
system browser. This plan:
- widens what the role hears into one value, `WebLink` (address, origin, placement, OSC 8 target, window);
- gives jasper-terminal a public link value carrying the click's gesture and the link's target;
- teaches the app to pick the split direction from the clicked pane's size.

No browser plugin exists yet. With none registered, everything still goes to the system browser or the Default editor,
as today.

**Tech Stack:** Java 25, Swing, JediTerm 3.76 (`jediterm-core`), JUnit 5, the SDK testkit.

**Spec:** [docs/superpowers/specs/2026-10-26-jasper-browser-plugin-design.md](../specs/2026-10-26-jasper-browser-plugin-design.md),
section 9 (all of it) and section 23 item 3. Plan 3 is
[2026-10-22-browser-3-link-routing.md](2026-10-22-browser-3-link-routing.md); this plan runs on its branch.

## Global Constraints

- **Branch:** `claude/browser-link-routing`, after its rebase onto `claude/browser-plugin-design` (on origin/main
  a67b37b14), in `.claude/worktrees/browser-link-routing`.
- **SDK version:** `0.23.0`. The rebase (2026-10-27, branch tip e64286da5) already set `JasperSdk.VERSION`, every
  `sdk = ">=0.23.0, <0.24"` range, and plan 3's `@since` tags. It also set Editor to 0.17.0 and Remote to 0.18.0, so
  this plan bumps no versions. Every public SDK member it adds carries `@since 0.23.0`.
- **The branch's baseline:** `./gradlew check` passed on 2026-10-27 on JBR 25.0.4.1 b635.75 with JCEF: 13,474 tests,
  0 failures, 0 errors, 30 skipped.
- **Full windows:** plan 3's `JasperApplicationPluginsTest` cases use `MainWindow` and `openFirstWindow`. If full
  windows reaches main first, follow its replacements.
- **The architecture rules:**
  - SDK types appear in the app only inside `dev.jasper.app.plugins`.
  - No public jasper-terminal method takes or returns a JediTerm type.
  - A new public terminal type joins the `supported` set in `build.gradle.kts:111-116` and the allowlist row in
    `jasper-terminal/README.md:44`.
- **Threading:**
  - JediTerm and the filter run on the session's reader thread.
  - `WebBrowser.open` and every `PluginRuntime` routing call run on the EDT and never block.
  - File checks stay off the EDT.
- **Source hygiene:** escape sequences are written as Java escapes (`"\033]8;;"`), never raw control characters.
- **Strings:** iTerm2's behaviour, with "iTerm2" read as "Jasper". This plan adds no user-visible text except docs.
- **No GUI launches.** Tests drive `TerminalView` through `handleMouse` and sessions through `FakeConnector`.
- **Commits** end with:

  ```
  🤖 Generated with Claude Code at The Home Depot

  Co-Authored-By: Claude <noreply@anthropic.com>
  ```

## Review Focus

1. **A split click in a pane that didn't have focus** must split *that* pane. Today the click opens the link
   synchronously, while the active group follows focus events later. Task 5 pins this.
2. **An OSC 8 target must not leak into the next link,** whether or not that link has a target. Task 3 pins this.
3. **Ctrl+Shift-click on Windows and Linux while a program asks for mouse reports** stays an ordinary open, not a
   split. A user in vim must still be able to open a link. Task 4 pins this.
4. **Selections that are almost a URL:** surrounding text, two URLs, a line break, a `mailto:` or `ftp:` address, or
   leading and trailing spaces. Only one trimmed `http(s)` URL with a host shows the right-click items. Task 6 pins
   this.
5. **Local web files:** `.HTML` in capitals counts; a folder named `site.html` and a missing file don't; and the browser
   is asked only for terminal clicks, not for `jasper file.html` or Open File. Task 7 pins this.

---

### Task 1: The SDK's link value

**Files:**
- Modify: `jasper-sdk/src/main/java/dev/jasper/sdk/ui/LinkOrigin.java`: `PROGRAM` becomes `EXTERNAL`.
- Create: `jasper-sdk/src/main/java/dev/jasper/sdk/ui/LinkPlacement.java`.
- Create: `jasper-sdk/src/main/java/dev/jasper/sdk/ui/WebLink.java`.
- Modify: `jasper-sdk/src/main/java/dev/jasper/sdk/ui/WebBrowser.java`: `open(WebLink)`.
- Modify: `jasper-sdk/src/main/java/dev/jasper/sdk/ui/Platform.java`: the Javadoc of `browser`, `openInBrowser` and
  `browse`.
- Modify: `jasper-sdk-testkit/.../FakePluginHost.java` (`openLink`, `browserPlugin`),
  `.../contract/ContractHarness.java` and `.../contract/PluginContractTest.java` (the browser cases, around 3696-3812).
- Test: `jasper-sdk/src/test/java/dev/jasper/sdk/ui/UiValuesTest.java`.

**Interfaces:**
- Produces:
  - `enum LinkOrigin { CLICK, EXTERNAL }`;
  - `enum LinkPlacement { TAB, SPLIT_RIGHT, SPLIT_DOWN }`;
  - `record WebLink(URI address, LinkOrigin origin, LinkPlacement placement, Optional<String> target, Optional<WindowOwner> window)`;
  - `boolean WebBrowser.open(WebLink link)`.
- Produces in the testkit:
  - `FakePluginHost.openLink(URI address, LinkOrigin origin, LinkPlacement placement, Optional<String> target, Optional<UUID> windowId) -> boolean`;
  - `ContractHarness.openLink` with the same parameters.

- [ ] **Step 1: Write the failing value tests** in `UiValuesTest`:

```java
@Test void aWebLinkTakesHttpAndHttpsAddressesWithAHost() {
    var link = new WebLink(URI.create("https://example.com/a"), LinkOrigin.CLICK, LinkPlacement.TAB, Optional.empty(), Optional.empty());
    assertEquals("https://example.com/a", link.address().toString());
    assertThrows(IllegalArgumentException.class, () -> new WebLink(URI.create("mailto:a@b.c"), LinkOrigin.CLICK, LinkPlacement.TAB, Optional.empty(), Optional.empty()));
    assertThrows(IllegalArgumentException.class, () -> new WebLink(URI.create("https:///x"), LinkOrigin.CLICK, LinkPlacement.TAB, Optional.empty(), Optional.empty()));
}
@Test void aWebLinkTakesALocalFileOnlyAsAClickInATab() {
    URI file = Path.of("/tmp/site/index.html").toUri();
    assertDoesNotThrow(() -> new WebLink(file, LinkOrigin.CLICK, LinkPlacement.TAB, Optional.empty(), Optional.empty()));
    assertThrows(IllegalArgumentException.class, () -> new WebLink(file, LinkOrigin.EXTERNAL, LinkPlacement.TAB, Optional.empty(), Optional.empty()));
    assertThrows(IllegalArgumentException.class, () -> new WebLink(file, LinkOrigin.CLICK, LinkPlacement.SPLIT_RIGHT, Optional.empty(), Optional.empty()));
}
@Test void aTargetIsOneToTwoHundredFiftySixCharactersWithoutControls() {
    assertThrows(IllegalArgumentException.class, () -> new WebLink(URI.create("https://e.com"), LinkOrigin.CLICK, LinkPlacement.TAB, Optional.of(""), Optional.empty()));
    assertThrows(IllegalArgumentException.class, () -> new WebLink(URI.create("https://e.com"), LinkOrigin.CLICK, LinkPlacement.TAB, Optional.of("a\nb"), Optional.empty()));
    assertThrows(IllegalArgumentException.class, () -> new WebLink(URI.create("https://e.com"), LinkOrigin.CLICK, LinkPlacement.TAB, Optional.of("x".repeat(257)), Optional.empty()));
    assertEquals("docs", new WebLink(URI.create("https://e.com"), LinkOrigin.CLICK, LinkPlacement.TAB, Optional.of("docs"), Optional.empty()).target().orElseThrow());
}
@Test void anExternalLinkHasNoTargetAndOpensInATab() {
    assertThrows(IllegalArgumentException.class, () -> new WebLink(URI.create("https://e.com"), LinkOrigin.EXTERNAL, LinkPlacement.SPLIT_DOWN, Optional.empty(), Optional.empty()));
    assertThrows(IllegalArgumentException.class, () -> new WebLink(URI.create("https://e.com"), LinkOrigin.EXTERNAL, LinkPlacement.TAB, Optional.of("t"), Optional.empty()));
}
```

- [ ] **Step 2: Run** `./gradlew :jasper-sdk:test --tests '*UiValuesTest'`. Expected: FAIL to compile.

- [ ] **Step 3: Implement the values.**
  - `WebLink`'s compact constructor enforces the rules the tests state:
    - an http or https address must be absolute and have a host;
    - a `file:` address must be absolute, and only `CLICK` with `TAB` may carry one;
    - `EXTERNAL` takes `TAB` and no target;
    - a target is 1 to 256 characters with no `Character.isISOControl` character.
  - **The Javadoc says:**
    - `EXTERNAL` is an address from outside Jasper: the `jasper` command or the system. It reaches the browser
      without the routing prompt;
    - `TAB` is "a tab, as the browser places it";
    - `SPLIT_*` asks for a split of the pane the link was clicked in, already the window's focused pane when `open`
      runs;
    - `target` is an OSC 8 `target=` name, so a browser can reuse the item it names.
  - **`WebBrowser.open(WebLink)` keeps plan 3's contract:** it runs on the UI thread, must not block, and returns true
    when it opened the link; false hands it back to Jasper. For a `file:` link that means the Default editor.

- [ ] **Step 4: Update the testkit and the contract cases.**
  - **Rename:** `PROGRAM` becomes `EXTERNAL` in the cases at 3728, 3749 and 3774.
  - **Add `aBrowserHearsThePlacementAndTarget`:** `openLink(PAGE, CLICK, SPLIT_DOWN, Optional.of("docs"), window)`
    reaches the recording browser as an equal `WebLink`, with the plugin's own `WindowOwner`.
  - **Add `aBrowserHearsALocalFileClick`:** a `file:` link reaches the browser. Returning false is reported back as
    false.
  - Both run on the fake and on the app runtime, as plan 3's cases do.

- [ ] **Step 5: Run** `./gradlew :jasper-sdk:test :jasper-sdk-testkit:test`. Expected: PASS.

- [ ] **Step 6: Commit** "SDK: a web link carries its placement and OSC 8 target; EXTERNAL replaces PROGRAM".

### Task 2: The app's runtime, and `jasper <url>` becomes EXTERNAL

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/plugins/WebBrowsers.java`: `open(...)` builds the `WebLink`.
- Modify: `jasper-app/src/main/java/dev/jasper/app/plugins/PluginRuntime.java`:
  - `openLink` takes a placement and a target;
  - `openProgramLink` becomes `openExternalLink`;
  - add the enum `Placement`.
- Modify: `jasper-app/src/main/java/dev/jasper/app/plugins/HostedContext.java`: `openInBrowser` uses `CLICK`, `TAB`
  and the active window.
- Modify: `jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java`:
  - remove the `OPEN_URL` command (around 874-881) and `openProgramAddress`;
  - `jasper <url>` uses `openExternalLink`.
- Modify: `jasper-app/src/main/java/dev/jasper/app/plugins/DescriptorParser.java`: drop `open-url` from
  `RESERVED_SUBCOMMANDS`.
- Test: `ApplicationCommandsTest`, `JasperApplicationPluginsTest` (around 593-715) and `DescriptorParserTest`.

**Interfaces:**
- Consumes: Task 1's `WebLink`, `LinkOrigin` and `LinkPlacement`.
- Produces, app-internal and free of SDK types outside `dev.jasper.app.plugins`:
  - `enum PluginRuntime.Placement { TAB, SPLIT_RIGHT, SPLIT_DOWN }`;
  - `void PluginRuntime.openLink(URI address, Placement placement, Optional<String> target, Optional<UUID> windowId)`
    (`CLICK`);
  - `void PluginRuntime.openExternalLink(URI address, Optional<UUID> windowId)` (`EXTERNAL`);
  - `boolean PluginRuntime.openLocalWebFile(Path file, Optional<UUID> windowId)`, used by Task 7. It is true when a
    browser took the file.

- [ ] **Step 1: Write the failing tests.**
  - **`ApplicationCommandsTest`:**
    - `aWebAddressGivenToTheCommandIsExternal`: `jasper https://example.com` reaches a recording browser with
      `EXTERNAL` and `TAB`;
    - `openUrlIsNoLongerACommand`: `jasper open-url https://example.com` is handled as the arguments `open-url` and
      the address, as any other word would be: the existing "no such file or folder" path for `open-url`.
  - **`DescriptorParserTest`:** a plugin may declare a `[[subcommands]]` named `open-url`.
  - **`JasperApplicationPluginsTest`:** replace the `PROGRAM` cases with `EXTERNAL`, and add
    `aSplitClickReachesTheBrowserAsSplitRight` through `PluginRuntime.openLink(..., Placement.SPLIT_RIGHT, Optional.of("t"), window)`.

- [ ] **Step 2: Run** the three test classes. Expected: FAIL.

- [ ] **Step 3: Implement.**
  - Map `Placement` to `LinkPlacement` inside `dev.jasper.app.plugins`.
  - A browser that returns false or throws still sends web addresses to `systemBrowser`.
  - A local file goes back to the caller as false.
  - Delete every `PROGRAM` and `open-url` path.

- [ ] **Step 4: Update the docs** (line numbers at b70ecb86c; find the same text after the rebase):
  - `docs/command-line.md` 91, 92, 176 and 247: `jasper URL...` opens in Jasper's browser without asking when one
    is provided; the `open-url` rows go;
  - `docs/plugin-authoring.md` 145, 216, 238, 1027-1033 and 1094-1113: the web browser role hears a `WebLink`;
    `CLICK` and `EXTERNAL`; split placements; targets; local files;
  - `docs/sdk-architecture.md` 401-413;
  - `docs/plugins.md` 376;
  - `jasper-sdk/README.md` 47, 50 and 74;
  - `jasper-sdk-testkit/README.md` 128-130;
  - the guide pages `tools/sdk-reference/guide/03-manifest.html` (38 and 85; the count of reserved names changes),
    `05-threading.html` 28, `06-capabilities.html` 41, `07-contributions.html` 31 and 255-257, and
    `13-testing.html` 167.

  Then run `./gradlew sdkReference`.

- [ ] **Step 5: Run** `./gradlew :jasper-app:test :jasper-sdk:check`. Expected: PASS, including `verifySdkReference`.

- [ ] **Step 6: Commit** "Link routing: jasper <url> is EXTERNAL; jasper open-url goes".

### Task 3: The terminal keeps each OSC 8 link's target

**Files:**
- Modify: `jasper-terminal/src/main/java/dev/jasper/terminal/internal/shell/ShellIntegrationFilter.java`: parse OSC 8,
  then emit `OSC 1341;jasper;link-target;NAME` before it.
- Modify: `.../internal/emulation/JediTermEngine.java`, around 106-108: handle `link-target`, and give the hyperlink
  filter the pending target.
- Modify: `.../internal/emulation/UriLink.java`: add a `target` field.
- Modify: `.../internal/text/CellAttributes.java`: add `String linkTarget` (null when none).
- Modify: `.../internal/emulation/JediCellReader.java`, around 109-122: copy the target.
- Modify: `.../internal/.../BufferQueries.java`, around 251-275: `linkAt` returns a `LinkHit`.
- Test: `ShellIntegrationFilterTest`, and a new `OscEightTargetTest` with `EmulationFixture`, `FakeConnector` and
  `Await`.

**Interfaces:**
- Produces, internal: `record LinkHit(String uri, Optional<String> target)`, in the package of `BufferQueries`.
- Produces: `Optional<LinkHit> BufferQueries.linkAt(int absoluteRow, int column)` replaces the `Optional<String>`
  version. Task 4 consumes it.

- [ ] **Step 1: Write the failing filter tests:**

```java
@Test void anOsc8LinkIsPrecededByItsTarget() {
    assertEquals("\033]1341;jasper;link-target;docs\007\033]8;id=1:target=docs;https://e.com\033\\",
        filtered("\033]8;id=1:target=docs;https://e.com\033\\"));
}
@Test void anOsc8LinkWithoutATargetClearsThePendingOne() {
    assertEquals("\033]1341;jasper;link-target;\007\033]8;;https://e.com\007", filtered("\033]8;;https://e.com\007"));
}
@Test void aTargetWithAControlCharacterOrOver256CharactersIsDropped() {
    assertTrue(filtered("\033]8;target=" + "x".repeat(257) + ";https://e.com\007").startsWith("\033]1341;jasper;link-target;\007"));
}
```

  Use the test class's existing helper that feeds text through the filter and collects its output, split across
  chunk boundaries as its other tests do.

- [ ] **Step 2: Write the failing session test** `OscEightTargetTest`:
  - Feed `"\033]8;target=docs;https://a.example\007A\033]8;;\007 \033]8;;https://b.example\007B\033]8;;\007"`.
  - `linkAt` on the `A` cell gives `LinkHit("https://a.example", Optional.of("docs"))`.
  - On the `B` cell it gives `LinkHit("https://b.example", Optional.empty())`, so the target doesn't leak.

- [ ] **Step 3: Run** `./gradlew :jasper-terminal:test --tests '*ShellIntegrationFilterTest' --tests '*OscEightTargetTest'`.
  Expected: FAIL.

- [ ] **Step 4: Implement.**
  - **The filter's parsing.** OSC 8's parameters are `key=value` pairs separated by `:`. The filter reads `target`,
    and passes the OSC 8 itself through unchanged.
  - **`link-target`** is the filter's own command, beside `cwd` and `mark`. `JediTermEngine` handles it on the reader
    thread before the other custom commands, storing a pending target.
  - **The hyperlink filter** (line 108) builds `new UriLink(uri, pendingTarget)` and then clears it.
  - `JediCellReader` keeps converting hyperlink styles each time, never caching them (AGENTS.md).

- [ ] **Step 5: Run** `./gradlew :jasper-terminal:test`. Expected: PASS.

- [ ] **Step 6: Commit** "Terminal: OSC 8 links keep their target".

### Task 4: The terminal's public link value and the split click

**Files:**
- Create: `jasper-terminal/src/main/java/dev/jasper/terminal/view/TerminalLink.java`.
- Modify: `.../view/TerminalView.java`: `setLinkOpener(Consumer<TerminalLink>)` (plan 3's public method, around
  298-309).
- Modify: `.../view/MouseController.java`, around 82-103: choose the gesture.
- Modify: `build.gradle.kts:111-116` (the `supported` set gains `view.TerminalLink` and `view.TerminalLink$Gesture`),
  `jasper-terminal/README.md:44` and its hooks table row (around 239-240), and `docs/terminal-architecture.md`
  (122-127 and 198-201).
- Test: `jasper-terminal/src/test/java/dev/jasper/terminal/view/TerminalViewInteractionTest.java`.

**Interfaces:**
- Consumes: Task 3's `LinkHit`.
- Produces:
  - `public record TerminalLink(String url, Gesture gesture, Optional<String> target)`;
  - `public enum Gesture { OPEN, SPLIT }`, nested;
  - `TerminalView.setLinkOpener(Consumer<TerminalLink> opener)`. With `null`, the default opener opens
    `link.url()` in the desktop's browser.

- [ ] **Step 1: Write the failing view tests** with `handleMouse`, an injected opener collecting into a list, and a
  fake platform flag in the existing style:
  - `cmdShiftClickIsASplitOnMacOs`: with and without mouse reports, the gesture is `SPLIT`.
  - `ctrlShiftClickIsASplitOnLinuxWithoutMouseReports`.
  - `ctrlShiftClickStaysAnOpenOnLinuxWhileAProgramAsksForMouseReports`: the gesture is `OPEN`, and the link is still
    opened.
  - `cmdClickIsAnOpenAndCarriesTheTarget`: given the OSC 8 feed of Task 3, the gesture is `OPEN` and the target is
    `docs`.
  - `aSplitClickOnAFilePathOpensTheFileAsToday`: the file handler receives it, and the link opener does not.

- [ ] **Step 2: Run** `./gradlew :jasper-terminal:test --tests '*TerminalViewInteractionTest'`. Expected: FAIL.

- [ ] **Step 3: Implement.**
  - **The gesture:** `SPLIT` when Shift is down together with the link modifier, and either the platform is macOS or
    no program asks for mouse reports. Otherwise `OPEN`.
  - **The record** requires a non-null, non-empty `url` and a non-null `target`. Its Javadoc states the gesture rule
    per platform.

- [ ] **Step 4: Update the allowlist and the docs,** then run `./gradlew :jasper-terminal:check verifyTerminalArchitecture`.
  Expected: PASS.

- [ ] **Step 5: Commit** "Terminal: a public link value with the click's gesture and target".

### Task 5: Clicks reach the browser with their placement

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/TabHost.java`, around 153-160:
  - activate the clicked pane;
  - compute `paneWide`;
  - pass a `LinkClick`.
- Create: `jasper-app/src/main/java/dev/jasper/app/workspace/LinkClick.java`.
- Modify: `.../workspace/WindowContent.java`, around 263 and 2647: `onOpenLink` becomes a `Consumer<LinkClick>`.
- Modify: `.../workspace/TerminalPane.java`, around 671, where the opener is cleared.
- Modify: `.../application/JasperApplication.java`, around 583 and 761-817: map the click to a `PluginRuntime.Placement`.
- Test: `jasper-app/src/test/java/dev/jasper/app/workspace/FileLinkClickTest.java`, extending plan 3's cases around
  197-230.

**Interfaces:**
- Consumes: Task 4's `TerminalLink`, and Task 2's `PluginRuntime.openLink(URI, Placement, Optional<String>, Optional<UUID>)`.
- Produces: `public record LinkClick(TerminalLink link, boolean paneWide)`. `paneWide` is `pane.getWidth() > pane.getHeight()`.

- [ ] **Step 1: Write the failing tests.**
  - `aSplitClickInAWidePaneIsSplitRight` and `aSplitClickInATallPaneIsSplitDown`: size the panes, click, and assert the
    recorded `LinkClick` and the placement `JasperApplication` maps it to.
  - `aSplitClickInAnUnfocusedPaneSplitsThatPane`:
    - setup: two panes, focus on the first;
    - action: a split click in the second;
    - expected: when the opener runs, the workspace's active group is the second pane's.
  - `theTargetReachesTheWindowsOpener`.

- [ ] **Step 2: Run** `./gradlew :jasper-app:test --tests '*FileLinkClickTest'`. Expected: FAIL.

- [ ] **Step 3: Implement.**
  - **Before `onOpenLink`,** `TabHost`'s opener selects the pane's tab and makes its group active synchronously,
    through the call the focus listener makes (around `TabHost:104`), as the right-click menu does at 156-159.
  - **`JasperApplication` maps the gesture:** `OPEN` is `TAB`, and `SPLIT` is `SPLIT_RIGHT` when `paneWide`, otherwise
    `SPLIT_DOWN`.
  - **Non-web links** (`ftp`, `mailto`) still go to `systemBrowser`.

- [ ] **Step 4: Run** `./gradlew :jasper-app:test`. Expected: PASS.

- [ ] **Step 5: Commit** "Link routing: terminal clicks carry their placement and target".

### Task 6: Right-click items for a selected web address

**Files:**
- Modify: `jasper-sdk/src/main/java/dev/jasper/sdk/ui/PluginMenu.java`: add `addForWebAddress`.
- Modify: `jasper-sdk/src/main/java/dev/jasper/sdk/ui/ActionContext.java` (or the type `ActionContext` is in): add
  `webAddress`.
- Modify: `jasper-app/.../WindowChrome.java`, around 733-759 (the context menu, rebuilt on every right-click):
  evaluate the pane's selection.
- Modify: `.../WindowContributions.java` (197-202), `Contributions.Invocation` (44) and `HostedUi.Context` (68 and
  264): carry the URI.
- Modify: `jasper-sdk-testkit/.../FakeUi.java`, around 287: model the filter and the context.
- Test: a `PluginContractTest` case, and a `WindowChrome` test in the app's existing style.

**Interfaces:**
- Produces:
  - `void PluginMenu.addForWebAddress(String actionId)`. It adds the action to a `menus().terminalContext()` section,
    shown only while the right-clicked pane's selection is one web address. Other menus throw
    `IllegalStateException("addForWebAddress belongs to the terminal's right-click menu")`.
  - `default Optional<URI> ActionContext.webAddress()`: the address when the action was chosen from such an item,
    else empty.
- **A web address,** for this rule, is the selection trimmed of leading and trailing spaces, with no whitespace or line
  break inside. It must parse as an absolute `http` or `https` URI with a host.

- [ ] **Step 1: Write the failing tests.**
  - **The contract case `aWebAddressItemShowsOnlyForAWebAddressSelection`:**
    - with the selection `"  https://example.com/x  "`, the item shows, and invoking it gives
      `webAddress() == https://example.com/x`;
    - with the selections `"see https://example.com"`, `"https://a.com https://b.com"`, `"https://a.com\nmore"`,
      `"mailto:a@b.c"`, `"ftp://h/x"` and `""`, the item is absent.
  - **`addForWebAddressOutsideTheTerminalMenuThrows`.**

- [ ] **Step 2: Run** the SDK, testkit and app tests. Expected: FAIL.

- [ ] **Step 3: Implement.**
  - **The host evaluates the rule** while building the menu, so no plugin code runs then. It reads the selection
    through the pane's buffer, as `PaneHandle.selection()` does, and passes the URI through `Invocation` into
    `Context`.
  - **No capability is needed:** the user chose the item with that selection. Say so in the Javadoc and in
    `docs/plugin-authoring.md`'s Menus section.

- [ ] **Step 4: Run** `./gradlew :jasper-sdk:check :jasper-sdk-testkit:test :jasper-app:test sdkReference`. Expected:
  PASS. Commit the regenerated `docs/sdk-reference.html`.

- [ ] **Step 5: Commit** "SDK: right-click items for a selected web address".

### Task 7: Local web files are offered to the browser

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/application/LocalWebFiles.java`.
- Modify: `.../application/JasperApplication.java`, around 735-770: terminal file clicks go through
  `openClickedFile`.
- Modify: `.../workspace/WindowContent.java` and `TabHost.openFileLink` (186-192), only to mark a click as a terminal
  click when they don't already.
- Test: `LocalWebFilesTest`, and a case in `FileLinkClickTest`.

**Interfaces:**
- Consumes: Task 2's `PluginRuntime.openLocalWebFile(Path, Optional<UUID>)`.
- Produces:
  - `static boolean LocalWebFiles.matches(Path file)`. It is true for a regular file whose extension, in any case, is
    `html`, `htm`, `xhtml`, `xml`, `svg`, `css`, `js`, `mjs`, `pdf`, `png`, `jpg`, `jpeg`, `gif`, `webp` or `heic`.
    These are iTerm2's HTML, XML, SVG, CSS, JavaScript, PDF, PNG, JPEG, GIF, WebP and HEIC types.
  - `JasperApplication.openClickedFile(Path file, int line, UUID windowId)`.

- [ ] **Step 1: Write the failing tests.**
  - **`LocalWebFilesTest`:**
    - `index.HTML` matches;
    - `notes.md`, a folder `site.html` and a missing `gone.html` do not;
    - `archive.tar.gz` does not.
  - **`FileLinkClickTest`:**
    - `aClickedLocalWebFileIsOfferedToTheBrowserFirst`: a browser returning true gets it, and the editor does not;
    - `aDeclinedLocalWebFileOpensInTheDefaultEditor`;
    - `jasperFileHtmlStillOpensInTheEditor`: the command path never asks the browser.

- [ ] **Step 2: Run** the tests. Expected: FAIL.

- [ ] **Step 3: Implement.**
  - **`openClickedFile`** does the file checks on its virtual thread as `openFile` does.
  - **For a `LocalWebFiles.matches` file,** it asks `openLocalWebFile` on the EDT. When that is false, it continues to
    the Default editor path unchanged.
  - **Only terminal clicks use it.** Open File…, `jasper <file>` and dropped files keep `openFile`.

- [ ] **Step 4: Run** `./gradlew :jasper-app:test`. Expected: PASS.

- [ ] **Step 5: Commit** "Link routing: local web files clicked in a terminal are offered to the browser".

### Task 8: Record and check

**Files:**
- Modify: `docs/STATUS.md`, plan 3's entry: this amendment, its check, and the deviation below.
- Modify: this plan's status banner.

- [ ] **Step 1: Record the deviation from the spec.**
  - Section 23 puts the plugin-off notification for URLs from outside in plan 3's amendment.
  - It needs the "is Jasper the system's default browser" check, which belongs with registering as a browser. Both
    therefore move to plan 4.
  - Until then, `jasper <url>` with no browser provided opens the system browser.

- [ ] **Step 2: Run the source hygiene script** from AGENTS.md, then `./gradlew check`, then
  `./gradlew -p docs/examples/external-plugin -PjasperSdkDir="$PWD/build/sdk-release" check pluginZip`. Expected: PASS.
  Record the test counts from the XML.

- [ ] **Step 3: Commit** "STATUS: browser plan 3b's check".
