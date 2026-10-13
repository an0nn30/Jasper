# SDK 0.16.1: clipboard and Tools menu — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give plugins clipboard access through `Platform` (copy, self-clearing secret copy, capability-gated read) and a
Tools menu, move the bundled plugins onto them, and document both, as SDK 0.16.1.

**Architecture:** One application-wide `HostClipboard` (in `dev.jasper.app.platform`) owns the AWT clipboard and the
single pending secret; every plugin's `Platform` (in `HostedContext`) delegates to it, reports write failures in the
plugin's notification group and gates reads with `clipboard.read`. The testkit mirrors it with `FakeClipboard`; the shared
contract suite pins both. `StandardMenu.TOOLS` maps to a `MenuTarget.Slot.TOOLS` menu that `WindowChrome` adds after Tab
only while it has entries. A new rule in the plugin presentation gate forbids the AWT clipboard in plugin code.

**Tech Stack:** Java 25 (JetBrains Runtime), Swing/AWT, Gradle 9.7, JUnit 6.1.3, AssertJ 3.27.7.

**Spec:** `docs/superpowers/specs/2026-10-13-sdk-clipboard-and-tools-menu-design.md`

**Workspace:** worktree `~/projects/moray/.claude/worktrees/sdk-clipboard`, branch `claude/sdk-clipboard` (from main
`bfc0ba6d`). Run every command from the worktree root. Never build in `~/projects/moray` itself: the user runs
`:jasper-app:run` there.

## Global Constraints

- `JasperSdk.VERSION` is `"0.16.1"`. Manifests that use the new API declare `sdk = ">=0.16.1, <0.17"`.
- Capability id `clipboard.read`, constant `Capabilities.CLIPBOARD_READ`, appended last in `Capabilities.ALL`.
- Consent text: `Read text you copied to the clipboard (clipboard.read)`.
- Failed-copy notification title: `Could not copy`, posted with the cause in the calling plugin's default group.
- Secret lifetime: 30 seconds (`HostClipboard.SECRET_LIFETIME = Duration.ofSeconds(30)`); clearing writes `""` and only
  when the clipboard's text still equals the pending secret; one pending secret application-wide.
- `Platform.copy`, `copySecret`, `clipboardText` are UI-thread only in the application (`IllegalStateException` elsewhere);
  `null` text throws `NullPointerException`; empty text is allowed.
- Read audit: plugin id and character count only, never the text.
- Fake busy-clipboard exception: `IllegalStateException("The clipboard is held by another program")`.
- `StandardMenu.TOOLS` / `MenuTarget.Slot.TOOLS`; menu title `Tools`, after Tab, before plugins' top-level menus, present
  only while it has entries. View submenu renamed `Tool Windows`.
- Commits end with:
  ```
  🤖 Generated with Claude Code at The Home Depot

  Co-Authored-By: Claude <noreply@anthropic.com>
  ```
- Source hygiene (AGENTS.md): no raw control/private-use/unpaired-surrogate characters; Java escapes for non-ASCII in tests.
- No GUI launch, no benchmark, no AOT training. Do not push; merging needs the user.

## Spec interpretations

1. **Off-UI-thread calls** are tested on the application host only (`HostedClipboardThreadTest`), not in the shared
   contract suite: the testkit models no UI thread (plugin tests drive `FakePluginHost` from the test thread), so the fake
   cannot reject them without breaking every plugin test that copies from an action.
2. **A plugin presentation gate rule** (`PluginPresentationCheck`) forbids `Toolkit.getSystemClipboard` /
   `getSystemSelection` in plugin main classes except `dev.jasper.editor.ui.SystemClipboard`. It is the RED test of the
   migration task and keeps plugins on the SDK afterwards; the spec did not name it.
3. **Seams removed, not kept:** FileServer's and Indexer's `Environment.clipboard` and Vault's `SecretClipboard` factory
   go away; their tests assert through `FakePluginHost.clipboard()`, which exercises the real SDK path. `FilesScope.Hooks`
   and `ServerEditor.KeySources` keep their injected functions (their unit tests are unchanged; FileServer's
   paste-a-key source with and without text stays covered by `ServerEditorTest`).
4. **Vault's own "Could not copy from the vault" error disappears:** a busy clipboard is now reported by Jasper as
   "Could not copy"; `VaultScope` no longer takes a `NotificationGroup`.
5. **`VaultPluginTest.stopLocksTheVaultEvenWhenClipboardCleanupFails`** becomes `stopLocksTheVault`: clipboard cleanup at
   stop is now the host's, pinned by the contract suite.
6. **The Sample plugin** declares `sdk = ">=0.16.1, <0.17"` because it places its demo in Tools.

## Review Focus

1. **Two secrets in a row:** the first secret's 30-second clear must not clear the second early. Test:
   `HostClipboardTest.aSecondSecretRestartsTheClock` (Task 1).
2. **A clipboard holding no text (an image or files):** reading returns empty without an exception, and a pending
   secret's clear leaves it alone. Tests: `HostClipboardTest.nothingOnTheClipboardOrNoTextReadsAsEmpty`,
   `aSecretIsLeftAloneWhenTheClipboardNowHoldsSomethingElse` (Task 1).
3. **Another plugin stopping:** only the plugin that copied the secret clears it. Test:
   `HostClipboardTest.stoppingThePluginThatCopiedTheSecretClearsItAndOnlyThatPlugin` (Task 1).
4. **A busy clipboard at clear time** (the Swing timer firing while another program holds it): nothing escapes the timer
   and the pending secret ends. Test: `HostClipboardTest.aBusyClipboardThrowsAndLeavesThePendingSecretAlone` (Task 1).
5. **Multi-line and non-ASCII text** round-trips exactly. Test: `HostClipboardTest.copiesTextAndReadsItBack` (Task 1).

## File structure

| File | Task | Responsibility |
|---|---|---|
| `jasper-app/src/main/java/dev/jasper/app/platform/HostClipboard.java` (new) | 1 | the application clipboard and pending secret |
| `jasper-app/src/test/java/dev/jasper/app/platform/HostClipboardTest.java` (new) | 1 | its unit tests |
| `jasper-sdk/src/main/java/dev/jasper/sdk/ui/Platform.java` | 2 | `copy`, `copySecret`, `clipboardText` |
| `jasper-sdk/src/main/java/dev/jasper/sdk/Capabilities.java`, `JasperSdk.java` | 2 | `CLIPBOARD_READ`, version |
| `jasper-sdk/src/test/java/dev/jasper/sdk/{CapabilitiesTest,ui/KitBoundaryTest,files/FilesValuesTest,palette/PaletteValuesTest,terminal/TerminalValuesTest}.java` | 2 | version and capability pins |
| `jasper-app/src/main/java/dev/jasper/app/plugins/{PluginHost,HostedContext,PluginRuntime,DescriptorParser}.java` | 2 | host wiring, gate, teardown |
| `jasper-app/src/main/java/dev/jasper/app/pluginmanager/Capabilities.java` | 2 | consent text |
| `jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java` | 2 | shared clipboard, report copy, quit |
| `jasper-app/src/test/java/dev/jasper/app/plugins/{AppContractTest,DescriptorParserTest}.java`, `HostedClipboardThreadTest.java` (new), `pluginmanager/CapabilityDescriptionsTest.java` | 2 | host harness and app tests |
| `jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakeClipboard.java` (new), `FakePluginContext.java`, `FakePluginHost.java` | 2 | fake clipboard |
| `jasper-sdk-testkit/src/testFixtures/java/dev/jasper/sdk/testing/contract/{ContractHarness,PluginContractTest}.java`, `src/test/.../FakeContractTest.java` | 2, 3 | contract cases |
| `jasper-sdk/src/main/java/dev/jasper/sdk/ui/StandardMenu.java`, `jasper-app/.../contributions/{MenuTarget,Contributions,PanelEntry}.java`, `workspace/WindowChrome.java` | 3 | Tools menu, Tool Windows |
| `jasper-app/src/test/java/dev/jasper/app/{workspace/WindowChromeContributionsTest,contributions/PanelContributionsTest,plugins/RemoteShortcutConfigTest,workspace/ZenModeTest}.java` | 3 | menu tests |
| `plugins/sample/...` (`SamplePlugin.java`, `SamplePluginTest.java`, `plugin.toml`), `docs/plugin-authoring.md` example | 3 | Sample in Tools |
| `gradle/architecture/PluginPresentationCheck.java` | 4 | AWT clipboard rule |
| `plugins/{fileserver,indexer,remote,vault}/...` | 4 | migration |
| `docs/plugin-authoring.md`, `jasper-sdk/README.md`, `docs/sdk-architecture.md`, `docs/credential-vault.md`, `docs/STATUS.md` | 5 | documentation |

---

### Task 1: The host clipboard service

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/platform/HostClipboard.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/platform/HostClipboardTest.java`

**Interfaces:**
- Consumes: nothing from other tasks.
- Produces: `public final class HostClipboard` with `HostClipboard(Supplier<java.awt.datatransfer.Clipboard> clipboard,
  BiFunction<Duration, Runnable, Runnable> after)`, `static HostClipboard system()`, `static final Duration SECRET_LIFETIME`,
  `void copy(String text)`, `void copySecret(String text, String owner)`, `Optional<String> text()`,
  `void ownerStopped(String owner)`, `void quit()`. `copy`/`copySecret` throw `IllegalStateException` (busy) or
  `java.awt.HeadlessException` (no clipboard) and then change nothing.

- [ ] **Step 1: Write the failing tests**

Create `jasper-app/src/test/java/dev/jasper/app/platform/HostClipboardTest.java`:

```java
package dev.jasper.app.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class HostClipboardTest {
    boolean busy;
    final Clipboard system = new Clipboard("test") {
        @Override public synchronized void setContents(Transferable contents, ClipboardOwner owner) {
            if (busy) throw new IllegalStateException("cannot open system clipboard");
            super.setContents(contents, owner);
        }
    };
    final List<Runnable> timers = new ArrayList<>();
    final List<Duration> delays = new ArrayList<>();
    final HostClipboard clipboard = new HostClipboard(() -> system, (delay, task) -> {
        delays.add(delay);
        timers.add(task);
        return () -> timers.remove(task);
    });

    /** Fires every armed clear, as their delays would. */
    void elapse() { List.copyOf(timers).forEach(Runnable::run); }

    /** The user copies in another program. */
    void userCopies(String text) { system.setContents(new StringSelection(text), null); }

    @Test void copiesTextAndReadsItBack() {
        clipboard.copy("line one\nline two λ");
        assertThat(clipboard.text()).contains("line one\nline two λ");
        clipboard.copy("");
        assertThat(clipboard.text()).contains("");
    }

    @Test void nothingOnTheClipboardOrNoTextReadsAsEmpty() {
        assertThat(clipboard.text()).isEmpty();
        system.setContents(image(), null);
        assertThat(clipboard.text()).isEmpty();
    }

    @Test void aSecretIsClearedAfterThirtySecondsIfTheClipboardStillHoldsIt() {
        clipboard.copySecret("hunter2", "dev.example.a");
        assertThat(delays).containsExactly(Duration.ofSeconds(30));
        assertThat(clipboard.text()).contains("hunter2");
        elapse();
        assertThat(clipboard.text()).contains("");
        assertThat(timers).isEmpty();
    }

    @Test void aLaterUserCopyIsLeftAlone() {
        clipboard.copySecret("hunter2", "dev.example.a");
        userCopies("my own text");
        elapse();
        assertThat(clipboard.text()).contains("my own text");
    }

    @Test void aSecretIsLeftAloneWhenTheClipboardNowHoldsSomethingElse() {
        clipboard.copySecret("hunter2", "dev.example.a");
        system.setContents(image(), null);
        elapse();
        assertThat(system.getContents(null).isDataFlavorSupported(DataFlavor.imageFlavor)).isTrue();
    }

    @Test void aLaterCopyEndsThePendingSecret() {
        clipboard.copySecret("hunter2", "dev.example.a");
        clipboard.copy("plain");
        assertThat(timers).as("the clear was cancelled").isEmpty();
        clipboard.ownerStopped("dev.example.a");
        clipboard.quit();
        assertThat(clipboard.text()).contains("plain");
    }

    @Test void aSecondSecretRestartsTheClock() {
        clipboard.copySecret("first", "dev.example.a");
        clipboard.copySecret("second", "dev.example.a");
        assertThat(timers).as("only the second secret's clear is armed").hasSize(1);
        elapse();
        assertThat(clipboard.text()).contains("");
    }

    @Test void stoppingThePluginThatCopiedTheSecretClearsItAndOnlyThatPlugin() {
        clipboard.copySecret("hunter2", "dev.example.a");
        clipboard.ownerStopped("dev.example.b");
        assertThat(clipboard.text()).contains("hunter2");
        clipboard.ownerStopped("dev.example.a");
        assertThat(clipboard.text()).contains("");
        assertThat(timers).isEmpty();
    }

    @Test void quittingClearsAPendingSecret() {
        clipboard.copySecret("hunter2", "dev.example.a");
        clipboard.quit();
        assertThat(clipboard.text()).contains("");
        assertThat(timers).isEmpty();
    }

    @Test void aBusyClipboardThrowsAndLeavesThePendingSecretAlone() {
        clipboard.copySecret("hunter2", "dev.example.a");
        busy = true;
        assertThatThrownBy(() -> clipboard.copy("lost")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> clipboard.copySecret("lost", "dev.example.b")).isInstanceOf(IllegalStateException.class);
        assertThat(timers).as("the first secret's clear is still armed").hasSize(1);
        assertThatCode(this::elapse).as("clearing while busy throws nothing").doesNotThrowAnyException();
        busy = false;
        assertThat(timers).isEmpty();
        assertThat(clipboard.text()).as("nothing could be cleared").contains("hunter2");
    }

    @Test void nullTextIsRejected() {
        assertThatThrownBy(() -> clipboard.copy(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> clipboard.copySecret(null, "dev.example.a")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> clipboard.copySecret("x", null)).isInstanceOf(NullPointerException.class);
    }

    private static Transferable image() {
        return new Transferable() {
            @Override public DataFlavor[] getTransferDataFlavors() { return new DataFlavor[] {DataFlavor.imageFlavor}; }
            @Override public boolean isDataFlavorSupported(DataFlavor flavor) { return DataFlavor.imageFlavor.equals(flavor); }
            @Override public Object getTransferData(DataFlavor flavor) { return null; }
        };
    }
}
```

- [ ] **Step 2: Run the tests to see them fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.platform.HostClipboardTest'`
Expected: FAIL at `:jasper-app:compileTestJava`: `cannot find symbol ... class HostClipboard`.

- [ ] **Step 3: Implement `HostClipboard`**

Create `jasper-app/src/main/java/dev/jasper/app/platform/HostClipboard.java`:

```java
package dev.jasper.app.platform;

import java.awt.HeadlessException;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * The one clipboard Jasper and its plugins put text on. A secret copy is cleared later, when the plugin that copied it
 * stops, or at quit, but only while the clipboard still holds it; any later copy through here ends it. UI thread.
 */
public final class HostClipboard {
    /** How long a secret stays on the clipboard. */
    public static final Duration SECRET_LIFETIME = Duration.ofSeconds(30);

    private record Pending(String text, String owner, Runnable cancel) { }

    private final Supplier<Clipboard> clipboard;
    private final BiFunction<Duration, Runnable, Runnable> after;
    private Pending pending;

    /**
     * @param clipboard the clipboard, asked for on every use
     * @param after runs a task once after a delay, returning what cancels it
     */
    public HostClipboard(Supplier<Clipboard> clipboard, BiFunction<Duration, Runnable, Runnable> after) {
        this.clipboard = Objects.requireNonNull(clipboard, "clipboard");
        this.after = Objects.requireNonNull(after, "after");
    }

    /** The desktop's clipboard, with secrets cleared by a Swing timer. */
    public static HostClipboard system() {
        return new HostClipboard(() -> Toolkit.getDefaultToolkit().getSystemClipboard(), (delay, task) -> {
            var timer = new javax.swing.Timer((int) delay.toMillis(), event -> task.run());
            timer.setRepeats(false);
            timer.start();
            return timer::stop;
        });
    }

    /**
     * Puts text on the clipboard and ends any pending secret.
     *
     * @throws IllegalStateException when another program holds the clipboard; nothing changes
     * @throws HeadlessException when there is no clipboard; nothing changes
     */
    public void copy(String text) {
        put(text);
        end();
    }

    /** As {@link #copy}, then clears the clipboard after {@link #SECRET_LIFETIME}, when {@code owner} stops or at quit. */
    public void copySecret(String text, String owner) {
        Objects.requireNonNull(owner, "owner");
        put(text);
        end();
        Pending[] armed = new Pending[1];
        armed[0] = new Pending(text, owner, after.apply(SECRET_LIFETIME, () -> { if (pending == armed[0]) clear(); }));
        pending = armed[0];
    }

    /** The clipboard's text; empty when it holds none or cannot be read. */
    public Optional<String> text() {
        try {
            Transferable contents = clipboard.get().getContents(null);
            if (contents == null || !contents.isDataFlavorSupported(DataFlavor.stringFlavor)) return Optional.empty();
            return Optional.of((String) contents.getTransferData(DataFlavor.stringFlavor));
        } catch (HeadlessException | IllegalStateException | UnsupportedFlavorException | IOException unreadable) {
            return Optional.empty();
        }
    }

    /** Clears the pending secret if {@code owner} copied it. */
    public void ownerStopped(String owner) {
        if (pending != null && pending.owner().equals(owner)) clear();
    }

    /** Clears any pending secret: Jasper is quitting. */
    public void quit() {
        if (pending != null) clear();
    }

    private void clear() {
        Pending secret = pending;
        end();
        try {
            if (text().map(secret.text()::equals).orElse(false)) put("");
        } catch (IllegalStateException | HeadlessException unavailable) {
            // Another program holds the clipboard now; there is nothing Jasper can clear.
        }
    }

    private void end() {
        if (pending == null) return;
        pending.cancel().run();
        pending = null;
    }

    private void put(String text) {
        var selection = new StringSelection(Objects.requireNonNull(text, "text"));
        clipboard.get().setContents(selection, selection);
    }
}
```

- [ ] **Step 4: Run the tests to see them pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.platform.HostClipboardTest'`
Expected: `BUILD SUCCESSFUL`; `jasper-app/build/test-results/test/TEST-dev.jasper.app.platform.HostClipboardTest.xml` has
`tests="11" failures="0" errors="0"`.

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/platform/HostClipboard.java jasper-app/src/test/java/dev/jasper/app/platform/HostClipboardTest.java
git commit -F - <<'EOF'
Add the application clipboard service

One HostClipboard puts text on the AWT clipboard and keeps a single
pending secret, cleared after 30 seconds, when its owner stops or at
quit, but only while the clipboard still holds it.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 2: The SDK clipboard, wired into the host and the testkit

**Files:**
- Modify: `jasper-sdk/src/main/java/dev/jasper/sdk/ui/Platform.java`, `jasper-sdk/src/main/java/dev/jasper/sdk/Capabilities.java`,
  `jasper-sdk/src/main/java/dev/jasper/sdk/JasperSdk.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/plugins/PluginHost.java`, `HostedContext.java`, `PluginRuntime.java`,
  `DescriptorParser.java`; `jasper-app/src/main/java/dev/jasper/app/pluginmanager/Capabilities.java`;
  `jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java`
- Create: `jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakeClipboard.java`
- Modify: `jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakePluginContext.java`, `FakePluginHost.java`
- Modify: `jasper-sdk-testkit/src/testFixtures/java/dev/jasper/sdk/testing/contract/ContractHarness.java`, `PluginContractTest.java`;
  `jasper-sdk-testkit/src/test/java/dev/jasper/sdk/testing/FakeContractTest.java`
- Modify tests: `jasper-sdk/src/test/java/dev/jasper/sdk/CapabilitiesTest.java`, `ui/KitBoundaryTest.java`,
  `files/FilesValuesTest.java`, `palette/PaletteValuesTest.java`, `terminal/TerminalValuesTest.java`;
  `jasper-app/src/test/java/dev/jasper/app/plugins/AppContractTest.java`, `DescriptorParserTest.java`;
  `jasper-app/src/test/java/dev/jasper/app/pluginmanager/CapabilityDescriptionsTest.java`
- Create test: `jasper-app/src/test/java/dev/jasper/app/plugins/HostedClipboardThreadTest.java`

**Interfaces:**
- Consumes: `HostClipboard` from Task 1 (all methods listed there).
- Produces:
  - SDK `Platform.copy(String) -> boolean`, `Platform.copySecret(String) -> boolean`, `Platform.clipboardText() -> Optional<String>`.
  - `Capabilities.CLIPBOARD_READ = "clipboard.read"`; `JasperSdk.VERSION = "0.16.1"`.
  - `PluginHost.clipboard` (package field, type `dev.jasper.app.platform.HostClipboard`); `PluginRuntime.clipboard(HostClipboard)`.
  - Testkit `public final class FakeClipboard` with `Optional<String> text()`, `void setText(String)`,
    `List<String> copies()`, `void failWrites(boolean)`, `void expireSecrets()`; `FakePluginHost.clipboard()`.
  - `ContractHarness`: `Optional<String> clipboard()`, `void userCopies(String)`, `void clipboardBusy(boolean)`,
    `void expireClipboardSecret()`.

- [ ] **Step 1: Point the SDK pins at 0.16.1 and `clipboard.read`**

In `jasper-sdk/src/test/java/dev/jasper/sdk/CapabilitiesTest.java` replace the test method with:

```java
    @Test void theSdkIsVersionSixteenOneWithTheClipboardReadCapability() {
        assertThat(JasperSdk.VERSION).isEqualTo("0.16.1");
        assertThat(Capabilities.NETWORK_LISTEN).isEqualTo("network.listen");
        assertThat(Capabilities.CLIPBOARD_READ).isEqualTo("clipboard.read");
        assertThat(Capabilities.ALL).endsWith(Capabilities.NETWORK_LISTEN, Capabilities.CLIPBOARD_READ);
    }
```

In `jasper-sdk/src/test/java/dev/jasper/sdk/ui/KitBoundaryTest.java`, `files/FilesValuesTest.java` (line 54) and
`palette/PaletteValuesTest.java` (line 70) replace `isEqualTo("0.16.0")` with `isEqualTo("0.16.1")`.

In `jasper-sdk/src/test/java/dev/jasper/sdk/terminal/TerminalValuesTest.java` line 89 replace
`"files.source", "network.listen");` with `"files.source", "network.listen", "clipboard.read");`.

- [ ] **Step 2: Add the application's parser and consent tests**

In `jasper-app/src/test/java/dev/jasper/app/plugins/DescriptorParserTest.java` replace

```java
        assertThat(DescriptorParser.CAPABILITIES).isEqualTo(Set.of("terminal.observe", "terminal.selection",
            "terminal.inject", "terminal.open", "session.provide", "palette.contribute", "files.source", "network.listen"));
```

with

```java
        assertThat(DescriptorParser.CAPABILITIES).isEqualTo(Set.of("terminal.observe", "terminal.selection",
            "terminal.inject", "terminal.open", "session.provide", "palette.contribute", "files.source", "network.listen",
            "clipboard.read"));
```

and add after `acceptsTheNetworkListenCapability`:

```java
    @Test void acceptsTheClipboardReadCapability() throws Exception {
        PluginDescriptor descriptor = DescriptorParser.parse(FULL.replace("\"session.provide\"", "\"clipboard.read\""));
        assertThat(descriptor.capabilities()).contains("clipboard.read");
    }
```

In `jasper-app/src/test/java/dev/jasper/app/pluginmanager/CapabilityDescriptionsTest.java` add inside the class:

```java
    @Test void clipboardReadIsDescribed() {
        assertThat(Capabilities.describe("clipboard.read")).isEqualTo("Read text you copied to the clipboard (clipboard.read)");
    }
```

- [ ] **Step 3: Add the contract harness controls and the contract cases**

In `jasper-sdk-testkit/src/testFixtures/java/dev/jasper/sdk/testing/contract/ContractHarness.java` replace the last two
lines

```java
        List<java.net.URI> browsed();
}
```

with

```java
        List<java.net.URI> browsed();
        /** What the clipboard holds now; empty when it holds no text. */
        java.util.Optional<String> clipboard();
        /** As if the user copied this text in another program. */
        void userCopies(String text);
        /** Whether clipboard writes fail, as while another program holds the clipboard. */
        void clipboardBusy(boolean busy);
        /** Runs the pending secret's clear now, as its 30 seconds would. */
        void expireClipboardSecret();
}
```

In `jasper-sdk-testkit/src/testFixtures/java/dev/jasper/sdk/testing/contract/PluginContractTest.java` add these tests
immediately after `browseOpensOnlyWebAddressesAndReachesTheHost`:

```java
    @Test void copyPutsTextOnTheClipboardAndReadingNeedsClipboardRead() {
        var reader = new AtomicReference<PluginContext>();
        h.start(info("test.reader", Capabilities.CLIPBOARD_READ), Set.of(), Set.of(), reader::set);
        PluginContext writer = started("test.writer", Set.of());
        h.ui(() -> {
            assertThat(writer.platform().copy("line one\nline two")).isTrue();
            assertThat(reader.get().platform().clipboardText()).contains("line one\nline two");
            assertThatThrownBy(() -> writer.platform().clipboardText())
                .isInstanceOfSatisfying(MissingCapabilityException.class, failure -> assertThat(failure.capability()).isEqualTo("clipboard.read"));
            assertThatThrownBy(() -> writer.platform().copy(null)).isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> writer.platform().copySecret(null)).isInstanceOf(NullPointerException.class);
            assertThat(writer.platform().copy("")).isTrue();
        });
        assertThat(h.clipboard()).contains("");
    }

    @Test void aSecretIsClearedLaterOnlyIfTheClipboardStillHoldsIt() {
        PluginContext a = started("test.a", Set.of());
        h.ui(() -> assertThat(a.platform().copySecret("hunter2")).isTrue());
        assertThat(h.clipboard()).contains("hunter2");
        h.expireClipboardSecret();
        assertThat(h.clipboard()).as("cleared after its time").contains("");
        h.ui(() -> a.platform().copySecret("hunter3"));
        h.userCopies("something else");
        h.expireClipboardSecret();
        assertThat(h.clipboard()).as("a later copy by the user is left alone").contains("something else");
        h.ui(() -> { a.platform().copySecret("hunter4"); a.platform().copy("not secret"); });
        h.expireClipboardSecret();
        assertThat(h.clipboard()).as("a later copy ends the secret").contains("not secret");
    }

    @Test void stoppingThePluginClearsTheSecretItCopied() {
        PluginContext a = started("test.a", Set.of());
        h.ui(() -> a.platform().copySecret("hunter2"));
        h.stopAll();
        assertThat(h.clipboard()).contains("");
    }

    @Test void stoppingThePluginLeavesAPlainCopy() {
        PluginContext a = started("test.a", Set.of());
        h.ui(() -> a.platform().copy("kept"));
        h.stopAll();
        assertThat(h.clipboard()).contains("kept");
    }

    @Test void aBusyClipboardIsReportedInsteadOfThrown() {
        PluginContext a = started("test.a", Set.of());
        h.clipboardBusy(true);
        h.ui(() -> {
            assertThat(a.platform().copy("lost")).isFalse();
            assertThat(a.platform().copySecret("lost too")).isFalse();
        });
        h.flush();
        h.clipboardBusy(false);
        assertThat(h.notifications()).hasSize(2)
            .allSatisfy(line -> assertThat(line).startsWith("test.a|ERROR|Could not copy|").contains("|cause|"));
        assertThat(h.clipboard()).isEmpty();
    }
```

In `jasper-sdk-testkit/src/test/java/dev/jasper/sdk/testing/FakeContractTest.java` replace

```java
            @Override public void stopAll() { host.stopAll(); }
```

with

```java
            @Override public java.util.Optional<String> clipboard() { return host.clipboard().text(); }
            @Override public void userCopies(String text) { host.clipboard().setText(text); }
            @Override public void clipboardBusy(boolean busy) { host.clipboard().failWrites(busy); }
            @Override public void expireClipboardSecret() { host.clipboard().expireSecrets(); }
            @Override public void stopAll() { host.stopAll(); }
```

In `jasper-app/src/test/java/dev/jasper/app/plugins/AppContractTest.java`, in `newHarness()`, replace

```java
        PluginSettingsFiles files = host.environment.settingsFiles();
```

with

```java
        boolean[] clipboardBusy = {false};
        var clipboardContents = new java.awt.datatransfer.Clipboard("contract") {
            @Override public synchronized void setContents(java.awt.datatransfer.Transferable contents, java.awt.datatransfer.ClipboardOwner owner) {
                if (clipboardBusy[0]) throw new IllegalStateException("cannot open system clipboard");
                super.setContents(contents, owner);
            }
        };
        List<Runnable> clipboardClears = new CopyOnWriteArrayList<>();
        onEdt(() -> host.clipboard = new dev.jasper.app.platform.HostClipboard(() -> clipboardContents,
            (delay, task) -> { clipboardClears.add(task); return () -> clipboardClears.remove(task); }));
        PluginSettingsFiles files = host.environment.settingsFiles();
```

and replace

```java
                        @Override public List<java.net.URI> browsed() { return List.copyOf(browsed); }
```

with

```java
                        @Override public List<java.net.URI> browsed() { return List.copyOf(browsed); }
                        @Override public java.util.Optional<String> clipboard() { return onEdtValue(() -> host.clipboard.text()); }
                        @Override public void userCopies(String text) {
                            onEdt(() -> clipboardContents.setContents(new java.awt.datatransfer.StringSelection(text), null));
                        }
                        @Override public void clipboardBusy(boolean busy) { clipboardBusy[0] = busy; }
                        @Override public void expireClipboardSecret() { onEdt(() -> List.copyOf(clipboardClears).forEach(Runnable::run)); }
```

- [ ] **Step 4: Add the application-only thread test**

Create `jasper-app/src/test/java/dev/jasper/app/plugins/HostedClipboardThreadTest.java`:

```java
package dev.jasper.app.plugins;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import dev.jasper.sdk.Capabilities;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.plugin.PluginContext;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** The application host refuses clipboard calls off the UI thread; the testkit models no UI thread. */
class HostedClipboardThreadTest {
    @Test void clipboardCallsOffTheUiThreadThrow() {
        try (var harness = new AppContractTest().newHarness()) {
            var context = new AtomicReference<PluginContext>();
            harness.start(new PluginInfo("test.off", "Off", "1.0.0", Set.of(Capabilities.CLIPBOARD_READ)), Set.of(), Set.of(), context::set);
            var platform = context.get().platform();
            assertThatIllegalStateException().isThrownBy(() -> platform.copy("x")).withMessageContaining("UI thread");
            assertThatIllegalStateException().isThrownBy(() -> platform.copySecret("x")).withMessageContaining("UI thread");
            assertThatIllegalStateException().isThrownBy(platform::clipboardText).withMessageContaining("UI thread");
            assertThat(harness.clipboard()).isEmpty();
        }
    }
}
```

- [ ] **Step 5: Run the tests to see them fail**

Run: `./gradlew :jasper-sdk:test --continue :jasper-sdk-testkit:test :jasper-app:test --tests 'dev.jasper.app.plugins.*' --tests 'dev.jasper.app.pluginmanager.*'`
Expected: FAIL. `:jasper-sdk:compileTestJava` fails with `cannot find symbol ... CLIPBOARD_READ` (so the version pins
cannot run yet); the testkit and app test sources fail to compile on `copySecret`, `clipboardText`, `clipboard()`,
`CLIPBOARD_READ` and the `host.clipboard` field.

- [ ] **Step 6: Add the SDK surface**

In `jasper-sdk/src/main/java/dev/jasper/sdk/JasperSdk.java` set `public static final String VERSION = "0.16.1";`.

In `jasper-sdk/src/main/java/dev/jasper/sdk/Capabilities.java` replace

```java
    /** Every capability this SDK version knows. */
    public static final List<String> ALL = List.of(TERMINAL_OBSERVE, TERMINAL_SELECTION, TERMINAL_INJECT, TERMINAL_OPEN, SESSION_PROVIDE, PALETTE_CONTRIBUTE, FILES_SOURCE, NETWORK_LISTEN);
```

with

```java
    /**
     * Reading the text on the clipboard with {@code Platform.clipboardText()}; the clipboard often holds something the
     * user just copied elsewhere, such as a password. Writing needs no capability. @since 0.16.1
     */
    public static final String CLIPBOARD_READ = "clipboard.read";
    /** Every capability this SDK version knows. */
    public static final List<String> ALL = List.of(TERMINAL_OBSERVE, TERMINAL_SELECTION, TERMINAL_INJECT, TERMINAL_OPEN, SESSION_PROVIDE, PALETTE_CONTRIBUTE, FILES_SOURCE, NETWORK_LISTEN, CLIPBOARD_READ);
```

In `jasper-sdk/src/main/java/dev/jasper/sdk/ui/Platform.java` add `import java.util.Optional;` after
`import java.nio.file.Path;`, and replace the final `}` of the interface with:

```java

    /**
     * Puts text on the system clipboard. UI thread only; elsewhere it throws {@code IllegalStateException}. When the
     * clipboard cannot be written, for example while another program holds it, a failure is posted in the plugin's
     * notification group, never thrown.
     *
     * @param text the text, possibly empty
     * @return whether the text is on the clipboard
     * @since 0.16.1
     */
    boolean copy(String text);

    /**
     * As {@link #copy}, for text that should not linger, such as a password or a decrypted value: Jasper clears the
     * clipboard 30 seconds later, when the plugin stops and when Jasper quits, if the clipboard still holds this text.
     * A later copy, by the user or by any plugin, is left alone.
     *
     * @param text the text, possibly empty
     * @return whether the text is on the clipboard
     * @since 0.16.1
     */
    boolean copySecret(String text);

    /**
     * The clipboard's text. Needs {@code clipboard.read}. UI thread only; elsewhere it throws
     * {@code IllegalStateException}.
     *
     * @return the text, or empty when the clipboard holds no text or cannot be read
     * @throws dev.jasper.sdk.MissingCapabilityException without {@code clipboard.read}
     * @since 0.16.1
     */
    Optional<String> clipboardText();
}
```

- [ ] **Step 7: Wire the application host**

In `jasper-app/src/main/java/dev/jasper/app/plugins/PluginHost.java` replace

```java
    java.util.function.Function<String, String> shortcuts = action -> "";
```

with

```java
    java.util.function.Function<String, String> shortcuts = action -> "";
    /** The clipboard every plugin's Platform writes to; the application shares it with its own copies. */
    dev.jasper.app.platform.HostClipboard clipboard = dev.jasper.app.platform.HostClipboard.system();
```

In `jasper-app/src/main/java/dev/jasper/app/plugins/HostedContext.java`:

1. In `teardown`, replace `        notifications.close();` with

```java
        notifications.close();
        host.clipboard.ownerStopped(id);
```

2. In `platform()`, replace

```java
                            notifications.group().error("Could not open a web address", address.toString(), failure);
                        }
                    });
                }
            };
        }
```

with

```java
                            notifications.group().error("Could not open a web address", address.toString(), failure);
                        }
                    });
                }
                @Override public boolean copy(String text) {
                    requireOpen();
                    requireUi("Platform.copy");
                    Objects.requireNonNull(text, "text");
                    return copied(() -> host.clipboard.copy(text));
                }
                @Override public boolean copySecret(String text) {
                    requireOpen();
                    requireUi("Platform.copySecret");
                    Objects.requireNonNull(text, "text");
                    return copied(() -> host.clipboard.copySecret(text, id));
                }
                @Override public Optional<String> clipboardText() {
                    requireOpen();
                    requireUi("Platform.clipboardText");
                    gate.require(Capabilities.CLIPBOARD_READ);
                    Optional<String> text = host.clipboard.text();
                    gate.audit(Capabilities.CLIPBOARD_READ, "read " + text.map(String::length).orElse(0) + " characters from the clipboard");
                    return text;
                }
            };
        }

        /** Runs a clipboard write; a clipboard that cannot be written is posted in the plugin's group, never thrown. */
        private boolean copied(Runnable write) {
            try {
                write.run();
                return true;
            } catch (IllegalStateException | java.awt.HeadlessException unavailable) {
                log().log(System.Logger.Level.WARNING, "Could not copy to the clipboard", unavailable);
                notifications.group().error("Could not copy", unavailable);
                return false;
            }
        }
```

In `jasper-app/src/main/java/dev/jasper/app/plugins/PluginRuntime.java`:

1. Replace

```java
    /** Supplies the live application motion policy before plugin startup. */
```

with

```java
    private dev.jasper.app.platform.HostClipboard clipboard = dev.jasper.app.platform.HostClipboard.system();
    /** Supplies the application's clipboard before plugin startup, so plugins share it with Jasper's own copies. */
    public void clipboard(dev.jasper.app.platform.HostClipboard value) {
        if (host != null) throw new IllegalStateException("Plugins have already started");
        clipboard = Objects.requireNonNull(value);
    }
    /** Supplies the live application motion policy before plugin startup. */
```

2. Replace `        created.shortcuts = shortcuts;` with

```java
        created.shortcuts = shortcuts;
        created.clipboard = clipboard;
```

In `jasper-app/src/main/java/dev/jasper/app/plugins/DescriptorParser.java` replace

```java
        "terminal.inject", "terminal.open", "session.provide", "palette.contribute", "files.source", "network.listen");
```

with

```java
        "terminal.inject", "terminal.open", "session.provide", "palette.contribute", "files.source", "network.listen",
        "clipboard.read");
```

In `jasper-app/src/main/java/dev/jasper/app/pluginmanager/Capabilities.java` replace

```java
            case "network.listen" -> "Accept connections from other programs or computers, such as serving files";
```

with

```java
            case "network.listen" -> "Accept connections from other programs or computers, such as serving files";
            case "clipboard.read" -> "Read text you copied to the clipboard";
```

In `jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java`:

1. Replace `    private Path logFolder;` with

```java
    private Path logFolder;
    /** The clipboard Jasper and its plugins share. */
    private final dev.jasper.app.platform.HostClipboard clipboard = dev.jasper.app.platform.HostClipboard.system();
```

2. Replace `        plugins.shortcuts(this::shortcutLabel);` with

```java
        plugins.shortcuts(this::shortcutLabel);
        plugins.clipboard(clipboard);
```

3. Replace

```java
            java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new java.awt.datatransfer.StringSelection(text), null);
```

with

```java
            clipboard.copy(text);
```

4. Replace

```java
        List<CompletableFuture<?>> pluginWork = plugins == null ? List.of() : plugins.stop();
```

with

```java
        List<CompletableFuture<?>> pluginWork = plugins == null ? List.of() : plugins.stop();
        // Plugins cleared their own secrets as they stopped; this clears anything still pending.
        clipboard.quit();
```

- [ ] **Step 8: Add the fake clipboard to the testkit**

Create `jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakeClipboard.java`:

```java
package dev.jasper.sdk.testing;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The clipboard every fake plugin's {@code Platform} writes to, as the application's clipboard behaves: a secret copy is
 * cleared when its time is up ({@link #expireSecrets()}) or its plugin stops, if the clipboard still holds it.
 */
public final class FakeClipboard {
    private final List<String> copies = new CopyOnWriteArrayList<>();
    private String text;
    private boolean failWrites;
    private String pendingSecret;
    private String pendingOwner;

    FakeClipboard() { }

    /**
     * What the clipboard holds.
     *
     * @return its text, or empty before anything was copied
     */
    public synchronized Optional<String> text() { return Optional.ofNullable(text); }

    /**
     * Puts text on the clipboard as the user would in another program; a pending secret is then left alone.
     *
     * @param value the text
     */
    public synchronized void setText(String value) { text = Objects.requireNonNull(value, "value"); }

    /**
     * Every copy plugins made, in order.
     *
     * @return lines {@code copy|<plugin id>|<text>} and {@code secret|<plugin id>|<text>}
     */
    public List<String> copies() { return List.copyOf(copies); }

    /**
     * Makes writes fail as while another program holds the clipboard: copies return false and post the host's error.
     *
     * @param value whether writes fail
     */
    public synchronized void failWrites(boolean value) { failWrites = value; }

    /** Runs the pending secret's 30-second clear now. */
    public synchronized void expireSecrets() { clearPending(); }

    synchronized void copy(String owner, String value, boolean secret) {
        if (failWrites) throw new IllegalStateException("The clipboard is held by another program");
        text = value;
        copies.add((secret ? "secret|" : "copy|") + owner + "|" + value);
        pendingSecret = secret ? value : null;
        pendingOwner = secret ? owner : null;
    }

    synchronized void ownerStopped(String owner) { if (owner.equals(pendingOwner)) clearPending(); }

    private void clearPending() {
        if (pendingSecret != null && pendingSecret.equals(text)) text = "";
        pendingSecret = null;
        pendingOwner = null;
    }
}
```

In `jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakePluginHost.java`:

1. Replace `    final List<java.net.URI> browsed = new CopyOnWriteArrayList<>();` with

```java
    final List<java.net.URI> browsed = new CopyOnWriteArrayList<>();
    final FakeClipboard clipboard = new FakeClipboard();
```

2. Replace `    public List<java.net.URI> browsed() { return List.copyOf(browsed); }` with

```java
    public List<java.net.URI> browsed() { return List.copyOf(browsed); }

    /**
     * The clipboard every plugin's {@code Platform} writes to.
     *
     * @return the fake clipboard
     */
    public FakeClipboard clipboard() { return clipboard; }
```

3. In `teardown`, replace `        notifications.stopped(context.plugin().id());` with

```java
        notifications.stopped(context.plugin().id());
        clipboard.ownerStopped(context.plugin().id());
```

In `jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakePluginContext.java` replace

```java
                @Override public void browse(java.net.URI uri) { requireOpen(); host.browsed.add(webAddress(uri)); }
            };
        }
```

with

```java
                @Override public void browse(java.net.URI uri) { requireOpen(); host.browsed.add(webAddress(uri)); }
                @Override public boolean copy(String text) { requireOpen(); return copied(text, false); }
                @Override public boolean copySecret(String text) { requireOpen(); return copied(text, true); }
                @Override public Optional<String> clipboardText() {
                    requireOpen();
                    if (!info.capabilities().contains(dev.jasper.sdk.Capabilities.CLIPBOARD_READ))
                        throw new dev.jasper.sdk.MissingCapabilityException(info.id(), dev.jasper.sdk.Capabilities.CLIPBOARD_READ);
                    return host.clipboard.text();
                }
            };
        }

        /** As the application: a busy clipboard is posted in the plugin's group, never thrown. */
        private boolean copied(String text, boolean secret) {
            Objects.requireNonNull(text, "text");
            try {
                host.clipboard.copy(info.id(), text, secret);
                return true;
            } catch (IllegalStateException busy) {
                notifications.group().error("Could not copy", busy);
                return false;
            }
        }
```

- [ ] **Step 9: Run the tests to see them pass**

Run: `./gradlew :jasper-sdk:check :jasper-sdk-testkit:check :jasper-app:test --tests 'dev.jasper.app.plugins.*' --tests 'dev.jasper.app.pluginmanager.*' --tests 'dev.jasper.app.platform.*'`
Expected: `BUILD SUCCESSFUL`. In `jasper-sdk-testkit/build/test-results/test/TEST-dev.jasper.sdk.testing.FakeContractTest.xml`
and `jasper-app/build/test-results/test/TEST-dev.jasper.app.plugins.AppContractTest.xml` the five new contract tests pass;
`HostedClipboardThreadTest` 1/1, `CapabilityDescriptionsTest` 2/2, `DescriptorParserTest` passes; `:jasper-sdk:javadoc`
(doclint) passes.

- [ ] **Step 10: Commit**

```bash
git add jasper-sdk jasper-sdk-testkit jasper-app/src/main/java/dev/jasper/app/plugins jasper-app/src/main/java/dev/jasper/app/pluginmanager jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java jasper-app/src/test/java/dev/jasper/app/plugins jasper-app/src/test/java/dev/jasper/app/pluginmanager
git commit -F - <<'EOF'
Give plugins the clipboard through Platform (SDK 0.16.1)

copy and copySecret put text on Jasper's shared clipboard and report a
busy clipboard in the plugin's group; clipboardText needs the new
clipboard.read capability and is audited. Stopping a plugin clears the
secret it copied. The testkit models it with FakeClipboard and the
contract suite pins both hosts.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 3: The Tools menu and View › Tool Windows

**Files:**
- Modify: `jasper-sdk/src/main/java/dev/jasper/sdk/ui/StandardMenu.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/contributions/MenuTarget.java`, `Contributions.java`, `PanelEntry.java`;
  `jasper-app/src/main/java/dev/jasper/app/workspace/WindowChrome.java`
- Modify: `plugins/sample/src/main/java/dev/jasper/sample/SamplePlugin.java`, `plugins/sample/src/main/resources/plugin.toml`,
  `plugins/remote/src/main/java/dev/jasper/remote/RemotePlugin.java` (comment), `docs/plugin-authoring.md` (example block)
- Tests: `jasper-sdk-testkit/src/testFixtures/java/dev/jasper/sdk/testing/contract/PluginContractTest.java`,
  `jasper-app/src/test/java/dev/jasper/app/workspace/WindowChromeContributionsTest.java`,
  `jasper-app/src/test/java/dev/jasper/app/contributions/PanelContributionsTest.java`,
  `jasper-app/src/test/java/dev/jasper/app/plugins/RemoteShortcutConfigTest.java`,
  `jasper-app/src/test/java/dev/jasper/app/workspace/ZenModeTest.java`,
  `plugins/sample/src/test/java/dev/jasper/sample/SamplePluginTest.java`

**Interfaces:**
- Consumes: nothing from Tasks 1–2 (independent of the clipboard).
- Produces: `StandardMenu.TOOLS`; `MenuTarget.Slot.TOOLS`; the View submenu title `"Tool Windows"`.

- [ ] **Step 1: Write the failing tests**

In `PluginContractTest.java` add after `menusAreMutableTreesInIndependentSections`:

```java
    @Test void theToolsMenuTakesSectionsLikeTheOtherStandardMenus() {
        h.start(info("test.alpha"), Set.of(), Set.of(), context -> {
            context.actions().register(ActionSpec.of("test.alpha.run", "Run"), invoked -> { });
            context.menus().standard(StandardMenu.TOOLS).add("test.alpha.run");
        });
        h.start(info("test.beta"), Set.of(), Set.of(), context -> {
            context.actions().register(ActionSpec.of("test.beta.run", "Run"), invoked -> { });
            context.menus().standard(StandardMenu.TOOLS).add("test.beta.run");
        });
        assertThat(h.menu("TOOLS")).containsExactly("item:test.alpha.run", "===", "item:test.beta.run");
    }
```

In `WindowChromeContributionsTest.java` add after `menusGainSectionsTopLevelMenusAndContextEntries`:

```java
    @Test void toolsAppearsAfterTabOnlyWhileAPluginHasEntriesInIt() throws Exception {
        edt(() -> {
            var model = new Contributions();
            WindowContent owner = DesktopTestSupport.content(DesktopTestSupport.launcher(new ArrayDeque<>()));
            owner.connectContributions(model);
            model.addAction("dev.x.run", "Run Tool", null, List.of(), Optional.empty(), invocation -> { });
            model.addMenuSection(MenuTarget.topLevel("dev.x.menu", "Own")).set(List.of(new MenuEntry.Item("dev.x.run")));
            assertThat(titles(owner)).as("no entries, no menu").doesNotContain("Tools");
            MenuSection first = model.addMenuSection(MenuTarget.standard(MenuTarget.Slot.TOOLS));
            first.set(List.of(new MenuEntry.Item("dev.x.run")));
            assertThat(titles(owner)).containsSubsequence("Tab", "Tools", "Own");
            assertThat(items(tools(owner))).as("no separator before the first section").containsExactly("Run Tool");
            MenuSection second = model.addMenuSection(MenuTarget.standard(MenuTarget.Slot.TOOLS));
            second.set(List.of(new MenuEntry.Item("dev.x.run")));
            assertThat(items(tools(owner))).containsExactly("Run Tool", "---", "Run Tool");
            first.close();
            second.close();
            assertThat(titles(owner)).as("the last entry gone, the menu goes").doesNotContain("Tools");
        });
    }

    private static List<String> titles(WindowContent owner) {
        return owner.chrome().menuBar().menus().stream().map(JMenu::getText).toList();
    }

    private static JMenu tools(WindowContent owner) {
        return owner.chrome().menuBar().menus().stream().filter(menu -> menu.getText().equals("Tools")).findFirst().orElseThrow();
    }
```

Rename the submenu in the existing tests (each `"Tools"` below becomes `"Tool Windows"`):
- `jasper-app/src/test/java/dev/jasper/app/contributions/PanelContributionsTest.java` lines 34, 39 and 61:
  `new MenuEntry.Submenu("Tools", ...` → `new MenuEntry.Submenu("Tool Windows", ...`.
- `jasper-app/src/test/java/dev/jasper/app/plugins/RemoteShortcutConfigTest.java` line 129:
  `submenu.title().equals("Tools")` → `submenu.title().equals("Tool Windows")`.
- `jasper-app/src/test/java/dev/jasper/app/workspace/ZenModeTest.java` line 163: `child(view, "Tools")` →
  `child(view, "Tool Windows")`.

In `plugins/sample/src/test/java/dev/jasper/sample/SamplePluginTest.java` replace

```java
            assertThat(host.menu("VIEW")).containsExactly("item:dev.jasper.sample.demo");
```

with

```java
            assertThat(host.menu("TOOLS")).containsExactly("item:dev.jasper.sample.demo");
            assertThat(host.menu("VIEW")).isEmpty();
```

- [ ] **Step 2: Run the tests to see them fail**

Run: `./gradlew :jasper-sdk-testkit:test :jasper-plugin-sample:test :jasper-app:test --tests 'dev.jasper.app.workspace.WindowChromeContributionsTest' --tests 'dev.jasper.app.contributions.PanelContributionsTest' --tests 'dev.jasper.app.plugins.RemoteShortcutConfigTest' --tests 'dev.jasper.app.workspace.ZenModeTest' --continue`
Expected: FAIL: compilation of the testkit fixtures and app tests stops at `cannot find symbol ... TOOLS`.

- [ ] **Step 3: Add the Tools menu**

In `jasper-sdk/src/main/java/dev/jasper/sdk/ui/StandardMenu.java` replace

```java
    /** The Tab menu. */
    TAB
```

with

```java
    /** The Tab menu. */
    TAB,
    /**
     * The Tools menu, for utility commands. Jasper has none of its own, so it shows only while a plugin has entries in
     * it, after Tab and before plugins' own menus. @since 0.16.1
     */
    TOOLS
```

In `jasper-app/src/main/java/dev/jasper/app/contributions/MenuTarget.java` replace
`    public enum Slot { FILE, EDIT, VIEW, PANE, TAB }` with `    public enum Slot { FILE, EDIT, VIEW, PANE, TAB, TOOLS }`.

In `jasper-app/src/main/java/dev/jasper/app/workspace/WindowChrome.java`:

1. Replace `        standardMenus.put(MenuTarget.Slot.TAB, tab);` with

```java
        standardMenus.put(MenuTarget.Slot.TAB, tab);
        // Tools holds only plugin entries; rebuildContributedMenus adds it to the bar while it has any.
        standardMenus.put(MenuTarget.Slot.TOOLS, menu("Tools"));
```

2. Replace the whole `rebuildContributedMenus` method with:

```java
    private void rebuildContributedMenus() {
        contributedSections.forEach((menu, added) -> added.forEach(menu::remove));
        contributedSections.clear();
        contributedMenus.forEach(menuBar::remove);
        contributedMenus.clear();
        if (contributions != null) {
            var sections = contributions.model().menus();
            for (MenuSection section : sections) {
                if (section.target().type() != MenuTarget.Type.STANDARD) continue;
                List<Component> built = build(section.entries(), MenuBarItems.Item::new);
                if (built.isEmpty()) continue;
                JMenu menu = standardMenus.get(MenuTarget.Slot.valueOf(section.target().key()));
                List<Component> added = contributedSections.computeIfAbsent(menu, key -> new ArrayList<>());
                // Tools has no items of its own, so its first section needs no separator.
                if (menu.getMenuComponentCount() > 0) {
                    var separator = new JPopupMenu.Separator();
                    menu.add(separator); added.add(separator);
                }
                for (Component child : built) { menu.add(child); added.add(child); }
            }
            // The bar orders menus as they are added: Tools after Tab, then plugins' own menus.
            JMenu tools = standardMenus.get(MenuTarget.Slot.TOOLS);
            if (tools.getMenuComponentCount() > 0) { menuBar.add(tools); contributedMenus.add(tools); }
            for (MenuSection section : sections) {
                if (section.target().type() != MenuTarget.Type.TOP_LEVEL) continue;
                List<Component> built = build(section.entries(), MenuBarItems.Item::new);
                if (built.isEmpty()) continue;
                JMenu menu = observe(new JMenu(section.target().title()));
                built.forEach(menu::add);
                menuBar.add(menu); contributedMenus.add(menu);
            }
        }
        menuBar.revalidate(); menuBar.repaint();
    }
```

In `jasper-app/src/main/java/dev/jasper/app/contributions/Contributions.java` replace

```java
    /** One app-owned View section, "Tools", listing every panel's toggle action; gone when there are no panels. */
```

with

```java
    /** One app-owned View section, "Tool Windows", listing every panel's toggle action; gone when there are no panels. */
```

and `        panelsMenu.set(List.of(new MenuEntry.Submenu("Tools", toggles)));` with
`        panelsMenu.set(List.of(new MenuEntry.Submenu("Tool Windows", toggles)));`.

In `jasper-app/src/main/java/dev/jasper/app/contributions/PanelEntry.java` line 36 and
`plugins/remote/src/main/java/dev/jasper/remote/RemotePlugin.java` line 215 replace `View > Tools` with
`View > Tool Windows` in the comments.

- [ ] **Step 4: Move the Sample's demo to Tools**

In `plugins/sample/src/main/java/dev/jasper/sample/SamplePlugin.java` and in the `example:pluginui` block of
`docs/plugin-authoring.md` (line 88), replace `context.menus().standard(StandardMenu.VIEW).add(DEMO);` with
`context.menus().standard(StandardMenu.TOOLS).add(DEMO);`.

In `plugins/sample/src/main/resources/plugin.toml` replace `sdk = ">=0.16.0, <0.17"` with `sdk = ">=0.16.1, <0.17"`.

- [ ] **Step 5: Run the tests to see them pass**

Run: `./gradlew :jasper-sdk-testkit:test :jasper-plugin-sample:test :jasper-app:test --tests 'dev.jasper.app.workspace.*' --tests 'dev.jasper.app.contributions.*' --tests 'dev.jasper.app.plugins.*' --tests 'dev.jasper.app.documentation.*'`
Expected: `BUILD SUCCESSFUL`; the new contract test passes in `FakeContractTest` and `AppContractTest`;
`WindowChromeContributionsTest.toolsAppearsAfterTabOnlyWhileAPluginHasEntriesInIt` passes; `AppDocumentationTest`
passes (the guide's `pluginui` example matches the Sample source).

- [ ] **Step 6: Commit**

```bash
git add jasper-sdk/src/main/java/dev/jasper/sdk/ui/StandardMenu.java jasper-app/src/main/java/dev/jasper/app/contributions jasper-app/src/main/java/dev/jasper/app/workspace/WindowChrome.java jasper-app/src/test jasper-sdk-testkit plugins/sample plugins/remote/src/main/java/dev/jasper/remote/RemotePlugin.java docs/plugin-authoring.md
git commit -F - <<'EOF'
Add a Tools menu for plugins and rename View › Tool Windows

StandardMenu.TOOLS is a menu after Tab that exists only while a plugin
has entries in it; the View submenu that lists tool windows becomes
Tool Windows. The Sample plugin's demo moves to Tools.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 4: Move the bundled plugins onto the SDK clipboard

**Files:**
- Modify: `gradle/architecture/PluginPresentationCheck.java`
- FileServer: `plugins/fileserver/src/main/java/dev/jasper/fileserver/FileServerPlugin.java`, `src/main/resources/plugin.toml`,
  `src/test/java/dev/jasper/fileserver/FileServerPluginTest.java`
- Indexer: `plugins/indexer/src/main/java/dev/jasper/indexer/IndexerPlugin.java`, `src/main/resources/plugin.toml`,
  `src/test/java/dev/jasper/indexer/IndexerPluginTest.java`
- Remote: `plugins/remote/src/main/java/dev/jasper/remote/ui/tunnels/TunnelsUi.java`, `ui/sftp/SftpUi.java`, `src/main/resources/plugin.toml`
- Vault: `plugins/vault/src/main/java/dev/jasper/vault/VaultPlugin.java`, `ui/VaultScope.java`, `ui/VaultManagerWindow.java`,
  `src/main/resources/plugin.toml`; delete `ui/SecretClipboard.java` and `src/test/java/dev/jasper/vault/ui/SecretClipboardTest.java`;
  tests `VaultPluginTest.java`, `ui/VaultScopeTest.java`, `ui/VaultManagerWindowTest.java`

**Interfaces:**
- Consumes: `Platform.copy`, `copySecret`, `clipboardText`, `Capabilities.CLIPBOARD_READ`, `FakePluginHost.clipboard()`
  and `FakeClipboard` (Task 2).
- Produces: `VaultScope(LockManager, VaultService, Platform, BiConsumer<WindowHandle, Optional<UUID>>)`;
  `VaultManagerWindow(PluginContext, LockManager, VaultManager, VaultService, Supplier<Status>, Runnable)`;
  `FileServerPlugin.Environment` and `IndexerPlugin.Environment` without a `clipboard` component.

- [ ] **Step 1: Add the gate rule and its fixtures**

In `gradle/architecture/PluginPresentationCheck.java`:

1. After `    private static final String EDITOR_THEME = "dev/jasper/editor/ui/EditorTheme";` add

```java
    // Plugins reach the clipboard through Platform; only the Editor's Vim registers keep their own.
    private static final String EDITOR_CLIPBOARD = "dev/jasper/editor/ui/SystemClipboard";
```

2. Replace

```java
                if (owner.startsWith("javax/swing/BorderFactory") && !canvas)
                    problems.add(name + " builds ordinary borders: " + operation);
```

with

```java
                if (owner.startsWith("javax/swing/BorderFactory") && !canvas)
                    problems.add(name + " builds ordinary borders: " + operation);
                boolean awtClipboard = owner.equals("java/awt/Toolkit")
                    && (member.equals("getSystemClipboard") || member.equals("getSystemSelection"));
                if (awtClipboard && !(exactSource && sourceOwner.equals(EDITOR_CLIPBOARD)))
                    problems.add(name + " uses the AWT clipboard directly; use Platform.copy, copySecret or clipboardText: " + operation);
```

3. In `selfTest`, after `cases.put("fixture/InheritedStyle", ...);` add

```java
        cases.put("fixture/Clipboard", "class Clipboard { Object get() { return java.awt.Toolkit.getDefaultToolkit().getSystemClipboard(); } }");
```

replace

```java
                case "fixture/Style", "fixture/InheritedStyle", "fixture/Margin", "fixture/PreferredSize", "fixture/IconAssignment", "fixture/InheritedSize" -> "styles ordinary controls";
```

with

```java
                case "fixture/Style", "fixture/InheritedStyle", "fixture/Margin", "fixture/PreferredSize", "fixture/IconAssignment", "fixture/InheritedSize" -> "styles ordinary controls";
                case "fixture/Clipboard" -> "uses the AWT clipboard directly";
```

and in the `positive` map replace its last entry

```java
            "dev/jasper/editor/ui/EditorTheme", "class EditorTheme { void apply(javax.swing.JViewport v){v.setOpaque(false); v.setBackground(new java.awt.Color(123));} }");
```

with

```java
            "dev/jasper/editor/ui/EditorTheme", "class EditorTheme { void apply(javax.swing.JViewport v){v.setOpaque(false); v.setBackground(new java.awt.Color(123));} }",
            "dev/jasper/editor/ui/SystemClipboard", "class SystemClipboard { Object get() { return java.awt.Toolkit.getDefaultToolkit().getSystemClipboard(); } }");
```

- [ ] **Step 2: Run the gate to see it fail**

Run: `./gradlew verifyPluginArchitecture`
Expected: FAIL after `Presentation checker fixtures: 13 negative, 4 positive passed`, with
`dev/jasper/fileserver/FileServerPlugin uses the AWT clipboard directly; use Platform.copy, copySecret or clipboardText: java/awt/Toolkit.getSystemClipboard()Ljava/awt/datatransfer/Clipboard;`.

- [ ] **Step 3: Point the plugin tests at the SDK clipboard**

`plugins/fileserver/src/test/java/dev/jasper/fileserver/FileServerPluginTest.java`:
- In `INFO`, replace `Set.of(Capabilities.NETWORK_LISTEN, Capabilities.TERMINAL_OBSERVE, Capabilities.PALETTE_CONTRIBUTE)` with
  `Set.of(Capabilities.NETWORK_LISTEN, Capabilities.TERMINAL_OBSERVE, Capabilities.PALETTE_CONTRIBUTE, Capabilities.CLIPBOARD_READ)`.
- Delete `    final List<String> copied = new ArrayList<>();`.
- In both `FileServerPlugin.Environment(...)` constructions replace `copied::add, revealed::add` with `revealed::add`.
- Replace `assertThat(copied).containsExactly(fingerprint);` with
  `assertThat(host.clipboard().copies()).containsExactly("copy|dev.jasper.fileserver|" + fingerprint);`.
- Replace `assertThat(copied).containsExactly("http://127.0.0.1:" + port + "/");` with
  `assertThat(host.clipboard().copies()).containsExactly("copy|dev.jasper.fileserver|http://127.0.0.1:" + port + "/");`.

`plugins/indexer/src/test/java/dev/jasper/indexer/IndexerPluginTest.java`:
- Delete `    final List<String> clipboard = new ArrayList<>();`.
- In both `IndexerPlugin.Environment(...)` constructions replace `clipboard::add, home` with `home`.
- Replace `assertThat(clipboard).containsExactly(app.resolve("src/main.rs").toString());` with
  `assertThat(host.clipboard().copies()).containsExactly("copy|dev.jasper.indexer|" + app.resolve("src/main.rs"));`.
- Replace `assertThat(clipboard).containsExactly("build-01:/srv/app/src/main.rs");` with
  `assertThat(host.clipboard().copies()).containsExactly("copy|dev.jasper.indexer|build-01:/srv/app/src/main.rs");`.

`plugins/vault/src/test/java/dev/jasper/vault/ui/VaultScopeTest.java`:
- Add imports `dev.jasper.sdk.PluginInfo`, `dev.jasper.sdk.testing.FakePluginHost`, `java.util.Set`,
  `org.junit.jupiter.api.AfterEach`.
- Replace the fields `    String clipboard = "";` and `    final List<Runnable> armed = new ArrayList<>();` with

```java
    final FakePluginHost host = new FakePluginHost();
    final dev.jasper.sdk.ui.Platform platform = host.start(new PluginInfo("dev.jasper.vault", "Vault", "0.2.0", Set.of()),
        Set.of(), Set.of(), context -> { }).platform();
```

  and delete `    boolean clipboardBroken;`. Add `    @AfterEach void closeHost() { host.close(); }` after the fields.
- Replace the `VaultScope` construction in `scope(Path)` with

```java
        scope = new VaultScope(lock, service, platform, (owner, id) -> opened.add(id.map(UUID::toString).orElse("-")));
```

- Replace both `        assertThat(clipboard).isEmpty();` with `        assertThat(host.clipboard().copies()).isEmpty();`.
- Replace the test `copyingPutsTheSecretOnTheClipboardWithAClearArmedAndOpenSelectsTheRow` with

```java
    @Test void copyingPutsASecretOnTheClipboardThatExpiresAndOpenSelectsTheRow(@TempDir Path dir) {
        scope(dir);
        populate();
        List<PaletteRow> all = scope.search("", query).rows();
        scope.execute(row(all, "cred." + PROD), VaultScope.COPY_PASSWORD, query);
        assertThat(host.clipboard().text()).contains("s3cret");
        scope.execute(row(all, "cred." + PROD), VaultScope.COPY_USERNAME, query);
        assertThat(host.clipboard().text()).contains("deploy");
        assertThat(host.clipboard().copies()).as("usernames expire too")
            .containsExactly("secret|dev.jasper.vault|s3cret", "secret|dev.jasper.vault|deploy");
        host.clipboard().expireSecrets();
        assertThat(host.clipboard().text()).contains("");
        scope.execute(row(all, "cred." + KEY), VaultScope.OPEN, query);
        assertThat(opened).containsExactly(KEY.toString());
        int[] changes = {0};
        scope.onChanged(() -> changes[0]++);
        scope.changed();
        assertThat(changes[0]).isEqualTo(1);
    }
```

- Replace the test `aCopyThatFailsIsPostedWithItsCause` with

```java
    @Test void aCopyThatFailsIsReportedByJasper(@TempDir Path dir) {
        scope(dir);
        populate();
        host.clipboard().failWrites(true);
        scope.execute(row(scope.search("", query).rows(), "cred." + PROD), VaultScope.COPY_USERNAME, query);
        assertThat(host.notifications().lines()).singleElement().asString()
            .startsWith("dev.jasper.vault|ERROR|Could not copy|").endsWith("|IllegalStateException");
        assertThat(host.clipboard().text()).isEmpty();
    }
```

`plugins/vault/src/test/java/dev/jasper/vault/ui/VaultManagerWindowTest.java`: remove the `SecretClipboard` argument
(and the comma after it) from all seven `new VaultManagerWindow(...)` calls:

```bash
python3 - <<'PY'
import pathlib, re
p = pathlib.Path("plugins/vault/src/test/java/dev/jasper/vault/ui/VaultManagerWindowTest.java")
text = p.read_text(encoding="utf-8")
new, count = re.subn(r"new SecretClipboard\(\s*t(?:ext)?\s*->\s*\{\s*\},\s*Optional::empty,\s*c(?:lear)?\s*->\s*\{\s*\}\s*\),\s*", "", text)
assert count == 7, count
p.write_text(new, encoding="utf-8")
PY
```

`plugins/vault/src/test/java/dev/jasper/vault/VaultPluginTest.java`:
- Delete `import dev.jasper.vault.ui.SecretClipboard;`.
- Replace the test `stopLocksTheVaultEvenWhenClipboardCleanupFails` with

```java
    @Test void stopLocksTheVault() throws Exception { dev.jasper.vault.testsupport.KitViews.edt(() -> {
        VaultPlugin plugin = plugin();
        try (var host = new FakePluginHost()) {
            host.start(INFO, Set.of(), Set.of(), plugin);
            plugin.lockManager().create("hunter2!".toCharArray(), false);
            host.runBackground();
            host.flush();
            assertThat(plugin.lockManager().state()).isEqualTo(LockState.UNLOCKED);
            assertThatCode(plugin::stop).doesNotThrowAnyException();
            assertThat(plugin.lockManager().state()).isEqualTo(LockState.LOCKED);
        }
    }); }
```

Delete `plugins/vault/src/test/java/dev/jasper/vault/ui/SecretClipboardTest.java`.

- [ ] **Step 4: Run the plugin tests to see them fail**

Run: `./gradlew :jasper-plugin-fileserver:test :jasper-plugin-indexer:test :jasper-plugin-vault:test --continue`
Expected: FAIL: test compilation stops on the old `Environment` arity (`constructor Environment in record Environment
cannot be applied to given types`) and on `VaultScope`/`VaultManagerWindow` constructors.

- [ ] **Step 5: Move FileServer**

In `plugins/fileserver/src/main/java/dev/jasper/fileserver/FileServerPlugin.java`:
- In `record Environment(...)` remove the component `Consumer<String> clipboard,` and in `system()` remove
  `FileServerPlugin::copy,`.
- Replace `this::reveal, environment.clipboard(),` with `this::reveal, this::copy,`.
- Replace `id -> registry.password(id).ifPresent(environment.clipboard()),` with `id -> registry.password(id).ifPresent(this::copy),`.
- Replace `new ServerEditor.KeySources(() -> keyFiles(window), FileServerPlugin::pasted),` with
  `new ServerEditor.KeySources(() -> keyFiles(window), this::pasted),`.
- Replace `() -> copy.ifPresent(environment.clipboard())` with `() -> copy.ifPresent(this::copy)`.
- Replace the whole `static Optional<String> pasted()` method (with its comment) with

```java
    /** The clipboard's text, for a key or certificate pasted into the editor; empty when there is none. */
    private Optional<String> pasted() { return context.platform().clipboardText(); }
```

- Replace the whole `static void copy(String text)` method with

```java
    private void copy(String text) { context.platform().copy(text); }
```

- Remove the imports `java.awt.Toolkit`, `java.awt.HeadlessException`, `java.awt.datatransfer.DataFlavor`,
  `java.awt.datatransfer.StringSelection` and `java.awt.datatransfer.UnsupportedFlavorException`; then
  `grep -nE '\b(Toolkit|HeadlessException|DataFlavor|StringSelection|UnsupportedFlavorException)\b' plugins/fileserver/src/main/java/dev/jasper/fileserver/FileServerPlugin.java`
  prints nothing.

In `plugins/fileserver/src/main/resources/plugin.toml` set `sdk = ">=0.16.1, <0.17"` and
`capabilities = ["network.listen", "terminal.observe", "palette.contribute", "clipboard.read"]`.

- [ ] **Step 6: Move Indexer**

In `plugins/indexer/src/main/java/dev/jasper/indexer/IndexerPlugin.java`:
- Delete the Environment javadoc line `     * @param clipboard the system clipboard`, remove the component
  `Consumer<String> clipboard,` from `record Environment(...)`, and remove `IndexerPlugin::copy, ` from `system()`.
- Replace `this::copyText, environment.clipboard())` with `this::copyText, text -> context.platform().copy(text))`.
- Delete the `static void copy(String text)` method.
- Remove the imports `java.awt.Toolkit`, `java.awt.HeadlessException` and `java.awt.datatransfer.StringSelection`; then
  `grep -nE '\b(Toolkit|HeadlessException|StringSelection)\b' plugins/indexer/src/main/java/dev/jasper/indexer/IndexerPlugin.java`
  prints nothing.

In `plugins/indexer/src/main/resources/plugin.toml` set `sdk = ">=0.16.1, <0.17"`.

- [ ] **Step 7: Move Remote**

In `plugins/remote/src/main/java/dev/jasper/remote/ui/tunnels/TunnelsUi.java` replace

```java
            text -> Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null));
```

with

```java
            text -> context.platform().copy(text));
```

and delete `import java.awt.Toolkit;` and `import java.awt.datatransfer.StringSelection;`.

In `plugins/remote/src/main/java/dev/jasper/remote/ui/sftp/SftpUi.java` replace
`text->Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text),null)` with
`text->context.platform().copy(text)` and delete `import java.awt.Toolkit;` and `import java.awt.datatransfer.StringSelection;`.

In `plugins/remote/src/main/resources/plugin.toml` set `sdk = ">=0.16.1, <0.17"`.

- [ ] **Step 8: Move Vault**

In `plugins/vault/src/main/java/dev/jasper/vault/ui/VaultScope.java`:
- Replace `import dev.jasper.sdk.notifications.NotificationGroup;` with `import dev.jasper.sdk.ui.Platform;`.
- Replace the fields and constructor

```java
    private final SecretClipboard clipboard;
    private final BiConsumer<WindowHandle, Optional<UUID>> openManager;
    private final NotificationGroup notifications;
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    public VaultScope(LockManager lock, VaultService service, SecretClipboard clipboard,
                      BiConsumer<WindowHandle, Optional<UUID>> openManager, NotificationGroup notifications) {
        this.lock = lock; this.service = service; this.clipboard = clipboard; this.openManager = openManager;
        this.notifications = notifications;
    }
```

with

```java
    private final Platform platform;
    private final BiConsumer<WindowHandle, Optional<UUID>> openManager;
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    /** Copies go to Jasper's clipboard as secrets, so they are cleared after 30 seconds; Jasper reports a failed copy. */
    public VaultScope(LockManager lock, VaultService service, Platform platform, BiConsumer<WindowHandle, Optional<UUID>> openManager) {
        this.lock = lock; this.service = service; this.platform = platform; this.openManager = openManager;
    }
```

- Replace

```java
        lock.vault().account(descriptor.id()).ifPresent(account -> {
            try {
                if (verb.equals(COPY_USERNAME)) { clipboard.copySecret(account.username()); return; }
                char[] password = switch (account.auth()) {
                    case Auth.Password p -> p.password();
                    case Auth.KeyAndPassword both -> both.password();
                    case Auth.Key key -> null;
                };
                if (password != null) clipboard.copySecret(new String(password));
            } catch (RuntimeException failure) { notifications.error("Could not copy from the vault", failure); }
        });
```

with

```java
        lock.vault().account(descriptor.id()).ifPresent(account -> {
            if (verb.equals(COPY_USERNAME)) { platform.copySecret(account.username()); return; }
            char[] password = switch (account.auth()) {
                case Auth.Password p -> p.password();
                case Auth.KeyAndPassword both -> both.password();
                case Auth.Key key -> null;
            };
            if (password != null) platform.copySecret(new String(password));
        });
```

In `plugins/vault/src/main/java/dev/jasper/vault/ui/VaultManagerWindow.java`:
- Delete the field `    private final SecretClipboard clipboard;`.
- Replace the constructor signature `VaultService service, SecretClipboard clipboard, Supplier<Status> status, Runnable generate) {`
  with `VaultService service, Supplier<Status> status, Runnable generate) {`, and in its body replace
  `this.clipboard = clipboard; this.status = status;` with `this.status = status;`.
- Replace `shown.secretClipboard(clipboard::copySecret);` with `shown.secretClipboard(text -> context.platform().copySecret(text));`.
- Replace `manager.publicKey(id).thenAccept(clipboard::copy);` with `manager.publicKey(id).thenAccept(text -> context.platform().copy(text));`
  (`publicKey` completes on Vault's UI executor or immediately on the calling UI thread).

In `plugins/vault/src/main/java/dev/jasper/vault/VaultPlugin.java`:
- Delete `import dev.jasper.vault.ui.SecretClipboard;`, the field `    private final Supplier<SecretClipboard> clipboardFactory;`
  and the field `    private SecretClipboard clipboard;`.
- In the class comment replace `Owns the manager window, clipboard, status item` with `Owns the manager window, status item`.
- In the public constructor replace `, SwingUtilities::invokeLater, SecretClipboard::system);` with `, SwingUtilities::invokeLater);`.
- Replace the two package-private constructors

```java
    VaultPlugin(Function<PluginContext, DeviceSecrets> secretsFactory, Executor ui) {
        this(secretsFactory, ui, () -> new SecretClipboard(text -> { }, Optional::empty, clear -> { }));
    }

    VaultPlugin(Function<PluginContext, DeviceSecrets> secretsFactory, Executor ui, Supplier<SecretClipboard> clipboardFactory) {
        this.secretsFactory = secretsFactory; this.ui = ui; this.clipboardFactory = clipboardFactory;
    }
```

with

```java
    VaultPlugin(Function<PluginContext, DeviceSecrets> secretsFactory, Executor ui) {
        this.secretsFactory = secretsFactory; this.ui = ui;
    }
```

- Replace

```java
        clipboard = clipboardFactory.get();
        scope = new VaultScope(lock, service, clipboard, this::open, context.notifications().group());
```

with

```java
        scope = new VaultScope(lock, service, context.platform(), this::open);
```

- Replace `managerWindow = new VaultManagerWindow(context, lock, manager, service, clipboard, this::managerStatus, this::showGenerator);`
  with `managerWindow = new VaultManagerWindow(context, lock, manager, service, this::managerStatus, this::showGenerator);`.
- In `stop()`, replace

```java
            if (manager != null) manager.invalidate();
            try {
                if (clipboard != null) clipboard.close();
            } catch (RuntimeException ignored) {
                // Clipboard ownership may already have gone away during shutdown.
            } finally {
                if (lock != null) lock.lock();
            }
```

with

```java
            if (manager != null) manager.invalidate();
            // Jasper clears a secret this plugin copied when the plugin stops.
            if (lock != null) lock.lock();
```

Delete `plugins/vault/src/main/java/dev/jasper/vault/ui/SecretClipboard.java`. In
`plugins/vault/src/main/resources/plugin.toml` set `sdk = ">=0.16.1, <0.17"`.

- [ ] **Step 9: Run the gate and the plugin tests to see them pass**

Run: `./gradlew verifyPluginArchitecture :jasper-plugin-fileserver:test :jasper-plugin-indexer:test :jasper-plugin-remote:test :jasper-plugin-vault:test :jasper-plugin-editor:test`
Expected: `BUILD SUCCESSFUL`; the gate prints `Presentation checker fixtures: 13 negative, 4 positive passed` and no
problems; every plugin test class passes (`VaultScopeTest`, `VaultPluginTest`, `VaultManagerWindowTest`,
`FileServerPluginTest`, `IndexerPluginTest` included).

Then: `python3 -c "import pathlib; print([p for p in pathlib.Path('plugins').rglob('*.java') if 'SecretClipboard' in p.read_text(encoding='utf-8')])"`
prints `[]`.

- [ ] **Step 10: Commit**

```bash
git add -A gradle/architecture/PluginPresentationCheck.java plugins/fileserver plugins/indexer plugins/remote plugins/vault
git commit -F - <<'EOF'
Move the bundled plugins onto the SDK clipboard

FileServer, Indexer, Remote and Vault copy through Platform; FileServer
reads its pasted keys with clipboard.read, and Vault copies secrets with
copySecret, so its own SecretClipboard goes. The plugin presentation
gate now rejects the AWT clipboard outside the Editor's Vim registers.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 5: Documentation and the whole check

**Files:**
- Modify: `docs/plugin-authoring.md`, `jasper-sdk/README.md`, `docs/sdk-architecture.md`, `docs/credential-vault.md`, `docs/STATUS.md`

**Interfaces:**
- Consumes: everything above; documents it.
- Produces: nothing code depends on.

- [ ] **Step 1: The plugin authoring guide**

In `docs/plugin-authoring.md`:

1. Line 20: replace `files.source, network.listen` with `files.source, network.listen, clipboard.read`.
2. After the line `- **Menus** are mutable: \`clear()\` and \`add(...)\` rebuild a host list at any time.` add

```markdown
- **The Tools menu** (SDK 0.16.1) is the home for utility commands: `context.menus().standard(StandardMenu.TOOLS).add(id)`.
  Jasper has no tools of its own, so the menu shows only while some plugin has entries in it, after Tab and before
  plugins' own top-level menus.
```

3. Replace `View → Tools entry, so don't add a View menu item of your own that toggles it, and don't repeat the` with
   `View › Tool Windows entry, so don't add a View menu item of your own that toggles it, and don't repeat the`.
4. After the capability table row for `network.listen` add

```markdown
| `clipboard.read` | `Platform.clipboardText()`: read the text on the clipboard (SDK 0.16.1) |
```

5. Before `## Rules that matter` add

```markdown
## The clipboard

Since SDK 0.16.1, `context.platform()` puts text on the clipboard and reads it, on the UI thread (elsewhere the calls throw
`IllegalStateException`):

- `copy(text)` puts text on the clipboard. When it cannot be written, for example while another program holds it, Jasper
  posts "Could not copy" with the cause in your plugin's group and the call returns `false`; nothing is thrown.
- `copySecret(text)` is `copy` for text that should not linger, such as a password or a decrypted value. Jasper clears the
  clipboard 30 seconds later, when your plugin stops and when Jasper quits, but only if the clipboard still holds that
  text: a later copy, by the user or by any plugin, is left alone.
- `clipboardText()` returns the clipboard's text, or empty when it holds none or cannot be read. It needs the
  `clipboard.read` capability, because the clipboard often holds something the user just copied elsewhere, such as a
  password; Jasper logs each read with your plugin id and the number of characters, never the text.

Plugin code does not use the AWT clipboard (`Toolkit.getSystemClipboard()`): `verifyPluginArchitecture` rejects it in the
bundled plugins, except for the Editor's Vim registers. Declare `sdk = ">=0.16.1, <0.17"` when you use these methods.

```

6. Replace `` `<panel id>.toggle` action or View → Tools menu; `host.panels()` lists `id|title|ANCHOR`, with `` with
   `` `<panel id>.toggle` action or View › Tool Windows menu; `host.panels()` lists `id|title|ANCHOR`, with ``.
7. Replace

```markdown
drive a contributed scope as the palette would; `paletteOpens()`, `openedInEditor()`
and `browsed()` record what the plugin asked for.
```

with

```markdown
drive a contributed scope as the palette would; `paletteOpens()`, `openedInEditor()`
and `browsed()` record what the plugin asked for. `host.clipboard()` is the clipboard every plugin's `Platform` writes
to: `copies()` lists `copy|<plugin id>|<text>` and `secret|<plugin id>|<text>`, `text()` and `setText(value)` read and
set it as the user would, `failWrites(true)` makes it busy, and `expireSecrets()` runs the 30-second clear now. The fake
does not model the UI thread, so the application's off-thread `IllegalStateException` is not reproduced.
```

- [ ] **Step 2: The SDK README, the SDK architecture and the Vault guide**

In `jasper-sdk/README.md`:

1. Replace the text

```text
All bundled manifests now require `sdk = ">=0.16.0, <0.17"` (0.16.0 adds menu shortcuts:
```

with

```text
Bundled manifests require `sdk = ">=0.16.0, <0.17"`, or `>=0.16.1` where they use the clipboard or the Tools menu (0.16.1 adds `Platform.copy`, `copySecret`, `clipboardText`, `clipboard.read` and `StandardMenu.TOOLS`; 0.16.0 adds menu shortcuts:
```
2. Before `## File workflows and progress (0.7.5)` add

```markdown
## Clipboard and the Tools menu (SDK 0.16.1)

`Platform.copy(text)`, `copySecret(text)` and `clipboardText()` give plugins the clipboard on the UI thread. A write that
fails is posted as "Could not copy" in the plugin's group and returns `false`; a secret is cleared 30 seconds later, when
its plugin stops or when Jasper quits, if the clipboard still holds it. Reading needs the new `clipboard.read`
capability (`Capabilities.CLIPBOARD_READ`). `StandardMenu.TOOLS` is a Tools menu for utility commands, shown after Tab
only while a plugin has entries in it; the View submenu that lists tool windows is now View › Tool Windows. The testkit
models the clipboard with `FakePluginHost.clipboard()`. Plugins that use any of these declare `sdk = ">=0.16.1, <0.17"`.

```

In `docs/sdk-architecture.md` replace `Every panel gets a View → Tools entry for its` with
`Every panel gets a View › Tool Windows entry for its`.

In `docs/credential-vault.md` replace

```markdown
copies are cleared after 30 seconds if the clipboard still contains that copy. A later user copy
is left alone. The vault never types clipboard contents into a terminal.
```

with

```markdown
copies are secret copies to Jasper's clipboard (SDK `Platform.copySecret`): Jasper clears them after 30 seconds, when the
vault plugin stops and when Jasper quits, if the clipboard still contains that copy. A later user copy is left alone.
The vault never types clipboard contents into a terminal.
```

- [ ] **Step 3: Run the whole check**

Run: `./gradlew check --continue > build/sdk-clipboard-check.log 2>&1; echo "exit=$?"; tail -5 build/sdk-clipboard-check.log`
Expected: `exit=0`, `BUILD SUCCESSFUL`. Then count:

```bash
python3 - <<'PY'
import glob, re
t = f = e = s = 0
for p in glob.glob("**/build/test-results/test/*.xml", recursive=True):
    head = open(p, encoding="utf-8").read(800)
    get = lambda k: int(re.search(k + '="(\\d+)"', head).group(1))
    t += get("tests"); f += get("failures"); e += get("errors"); s += get("skipped")
print(f"tests={t} failures={f} errors={e} skipped={s}")
PY
```

Expected: `failures=0 errors=0`.

Then the source hygiene check from AGENTS.md:

```bash
python3 - <<'PY'
import pathlib
roots = ("jasper-terminal/src", "jasper-app/src", "jasper-buddy/src", "jasper-sdk/src", "jasper-sdk-testkit/src", "plugins", "gradle/architecture")
for root in roots:
    for p in pathlib.Path(root).rglob("*.java"):
        text = p.read_text(encoding="utf-8")
        bad = sum(1 for c in text if 0xD800 <= ord(c) <= 0xDFFF or 0xE000 <= ord(c) <= 0xF8FF or (ord(c) < 0x20 and c not in "\n\t\r") or ord(c) == 0x7f)
        if bad: print(p, "bad chars:", bad)
PY
```

Expected: no output. And `git diff --check main...HEAD` prints nothing.

- [ ] **Step 4: Record the work in STATUS**

In `docs/STATUS.md`, insert after the first line (`# Jasper — Status and Handoff`) and its blank line, using the
totals from Step 3:

```markdown
**SDK 0.16.1: clipboard and Tools menu — 2026-10-13** (`claude/sdk-clipboard`, worktree
`.claude/worktrees/sdk-clipboard`; [spec](superpowers/specs/2026-10-13-sdk-clipboard-and-tools-menu-design.md),
[plan](superpowers/plans/2026-10-13-sdk-clipboard-and-tools-menu.md)). `Platform.copy`, `copySecret` and
`clipboardText` (behind the new `clipboard.read` capability) go through one `HostClipboard` shared with Jasper's own
copies: a busy clipboard is posted as "Could not copy" in the plugin's group, and a secret is cleared after 30 seconds,
when its plugin stops or at quit, only while the clipboard still holds it. `StandardMenu.TOOLS` adds a Tools menu after
Tab that exists only while a plugin has entries; View › Tools is now View › Tool Windows. FileServer, Indexer, Remote
and Vault moved onto the SDK clipboard (Vault's `SecretClipboard` is gone); `PluginPresentationCheck` now rejects the AWT
clipboard in plugins except the Editor's Vim registers. The Sample's demo is in Tools. Learned from porting an external
encrypt/decrypt plugin. `./gradlew check --continue`: **<tests> tests, 0 failures, 0 errors, <skipped> skipped**. No GUI
launched. GUI checks for the user: Tools › Run Sample Activity after Tab in a dev run; View › Tool Windows; a Vault copy
cleared after 30 seconds and a later copy left alone; FileServer's Copy Address and paste-a-key; macOS native menu bar
shows Tools only while a plugin has entries. Not merged or pushed.

```

replacing `<tests>` and `<skipped>` with the numbers Step 3 printed.

- [ ] **Step 5: Commit**

```bash
git add docs/plugin-authoring.md jasper-sdk/README.md docs/sdk-architecture.md docs/credential-vault.md docs/STATUS.md
git commit -F - <<'EOF'
Document the SDK clipboard and the Tools menu

The authoring guide gains a clipboard section, the Tools menu, the
clipboard.read capability and the testkit's clipboard; the SDK README,
SDK architecture, Vault guide and STATUS follow.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

## After the last task

- Whole-branch review against the spec and this plan (`git merge-base main HEAD`..`HEAD`).
- Report the `./gradlew check` totals.
- Ask the user before merging `claude/sdk-clipboard` into `main` or pushing; hand over the STATUS GUI checks.
- Then the next pieces agreed with the user: publishing the SDK with a `dev.jasper.plugin` Gradle convention, and moving
  the external encrypt/decrypt plugin to 0.16.1 (it would use `copySecret` for decrypted values and `StandardMenu.TOOLS`).
