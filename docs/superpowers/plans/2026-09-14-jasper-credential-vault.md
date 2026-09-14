# Jasper Credential Vault Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Port the reviewed vault core from `codex/rail-vault-implementation` onto main as `dev.jasper.app.vault`, then build a new vanilla-Swing credential manager with a status-bar padlock and a Tools menu, so later SSH/SFTP work can resolve logins and SSH keys from one encrypted vault.

**Architecture:** The core (`VaultService`, encrypted file, key import, OS keychain adapters) is copied file-for-file with a package rename and four brand fixes; its tests come with it. New package-private UI classes in `dev.jasper.app` sit on top: `VaultController` (one per application, single worker thread, generation checks), `VaultActivity` (inactivity tracking), `VaultManagerPanel` and forms (lightweight components for headless tests), `VaultManagerWindow` (the only JFrame boundary), and small hooks in `WindowContent`, `WindowChrome`, `WindowStatusBar`, `WindowCommands`, `TerminalWindow`, `JasperApplication` and `Main`.

**Tech Stack:** Java 25 on JBR, Swing (Metal/Motif/Nimbus built-ins), JUnit 6 + AssertJ, Bouncy Castle 1.85.x, Apache MINA `sshd-common` 2.18.0, JNA 5.14.0, Gradle wrapper.

**Spec:** `docs/superpowers/specs/2026-09-14-jasper-credential-vault-design.md` (this branch). Background: `docs/superpowers/specs/2026-09-13-jasper-vault-technical-design.md` (file format, limits, locking) and `docs/superpowers/specs/2026-09-13-jasper-vault-product-design.md` (product contract).

## Global Constraints

- Branch `codex/credential-vault`; never commit on `main`. Every commit ends with `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>`.
- Use `./gradlew` only. `./gradlew check` must pass at the end of every task (headless). Read `jasper-app/build/test-results/test/*.xml` for counts.
- Never launch the GUI (`:jasper-app:run`) or the benchmark. Preview tasks are headless and opt-in.
- Everything lives in `jasper-app`. `jasper-terminal` is untouched. No new module, no plugin API, no interface without two real implementations (`DeviceAccessStore` already has four).
- Package `dev.jasper.app.vault` is the ported core; UI classes are package-private in `dev.jasper.app`.
- Dependencies added to `jasper-app` only: `org.bouncycastle:bcprov-jdk18on:1.85.2`, `org.bouncycastle:bcpkix-jdk18on:1.85`, `org.apache.sshd:sshd-common:2.18.0`, `net.java.dev.jna:jna:5.14.0` (same version pty4j already brings). No `sshd-core`.
- Brand fixes in the ported core, exactly: file magic `MORAYVLT` becomes `JASPRVLT` (`0x4A41535052564C54L`), keychain label "Jasper remembered vault access", Windows credential user name "Jasper", temp-file prefix `.jasper-vault-`. Nothing else in the core changes.
- Credential types: logins and SSH keys only. No API keys, secure notes, key generation, rekey, export, SSH, rail or host associations.
- Vault file: `vault.enc` and sidecar `vault.enc.device` in `AppDirs.root()`.
- New actions `VAULT_MANAGER` ("Credential Vault…") and `VAULT_LOCK` ("Lock Vault" / "Unlock Vault…") have no default shortcut (`"none"`); both are in the command palette.
- Secrets are `char[]`/`byte[]`; wipe on close/lock; never log secrets; error text never includes native exception payloads.
- Source hygiene: no raw control/private-use characters in source; write `…` etc. as Java escapes. Check with the Python snippet in `AGENTS.md` before each commit.
- Every EDT-touching test uses `SwingUtilities.invokeAndWait` (`DesktopTestSupport.edt`). Tests use temp directories, an in-memory `DeviceAccessStore` and injected clocks; they never touch the real keychain.

## File map

Ported core (`jasper-app/src/main/java/dev/jasper/app/vault/`): `CredentialMaterial`, `DeviceAccessStore`, `DeviceAccessStores`, `DeviceAccessSupport`, `LinuxDeviceAccessStore`, `MacDeviceAccessStore`, `NativeCredentialCalls`, `VaultCrypto`, `VaultData`, `VaultFiles`, `VaultKeys`, `VaultService`, `VaultSettings`, `VaultSnapshot`, `WindowsDeviceAccessStore`. Ported tests (`jasper-app/src/test/java/dev/jasper/app/vault/`): `DeviceAccessStoresTest`, `FakeCredentialCalls`, `LinuxDeviceAccessStoreTest`, `MacDeviceAccessStoreTest`, `VaultServiceTest`, `VaultStorageTest`, `WindowsDeviceAccessStoreTest`.

New UI (`jasper-app/src/main/java/dev/jasper/app/`):

| File | Responsibility |
|---|---|
| `VaultIcons.java` | 16 px Java2D outline icons (lock, unlock, login, key, eye, copy, import, settings, save, add). |
| `VaultUi.java` | Layout helpers: `action`, `row`, `form`, `field`, `pad`, `changes`, `enabled`. |
| `VaultLoginForm.java` | Login editor/draft (name, username, key combo, password with reveal/copy). |
| `VaultKeyForm.java` | Key editor (name, algorithm, fingerprint, public key with copy, reuse count). |
| `VaultUnlockForm.java` | Create/unlock form with remember checkbox and expiry. |
| `VaultAddLoginDialog.java` | Add credential body (form + Import SSH key… + Cancel/Add login). |
| `VaultImportForm.java` | Choose file, name, passphrase, inspect, fingerprint preview, import. |
| `VaultSettingsForm.java` | Auto-lock minutes, remember days, forget device. |
| `VaultManagerPanel.java` | Toolbar row, sidebar list, table, editor, status strip. |
| `VaultActivity.java` | AWT input listener over registered roots + 1 s inactivity timer. |
| `VaultController.java` | Application-owned state machine between service, panel, dialogs and windows. |
| `VaultManagerWindow.java` | JFrame + JDialog + JFileChooser boundary for the controller. |
| `VaultWindowBinding.java` | Connects one `WindowContent` to the controller (status, menu, activity root). |

Modified: `jasper-app/build.gradle.kts`, `ActionId.java`, `KeyBindings.java`, `WindowStatusBar.java`, `WindowChrome.java`, `WindowCommands.java`, `WindowContent.java`, `TerminalWindow.java`, `JasperApplication.java`, `Main.java`, `KeyBindingsTest.java`, docs.

---

### Task 1: Dependencies and core port

**Files:**
- Modify: `jasper-app/build.gradle.kts` (dependencies block)
- Create: the 15 main and 7 test files listed in the file map, under `jasper-app/src/{main,test}/java/dev/jasper/app/vault/`
- Create: `jasper-app/src/test/java/dev/jasper/app/vault/VaultBrandTest.java`

**Interfaces:**
- Produces (public, package `dev.jasper.app.vault`): `VaultService(Path, DeviceAccessStore, Clock)` and `VaultService(Path, DeviceAccessStore, Clock, LongSupplier nanoTime)`; `VaultSnapshot snapshot()`; `void create(char[] password, Duration rememberFor)`; `void unlock(char[] password, Duration rememberFor)`; `boolean unlockRemembered()`; `void lock(VaultService.LockReason)` with `enum LockReason { AUTO, EXPLICIT }`; `void forgetDeviceAccess()`; `void userActivity()`; `boolean checkInactivity()`; `KeyPreview inspectKey(byte[], char[])` with `record KeyPreview(String algorithm, String fingerprint, String publicKey)`; `ImportResult importKey(String name, byte[] privateBytes, char[] passphrase)` with `record ImportResult(UUID id, boolean duplicate)`; `UUID saveLogin(UUID id, String name, String username, char[] password, UUID keyId)`; `void renameKey(UUID, String)`; `void deleteLogin(UUID)`; `void deleteKey(UUID)`; `void saveSettings(VaultSettings)`; `CredentialMaterial resolveLogin(UUID)`; `CredentialMaterial resolveKey(UUID, String username)`; `void close()`.
- `record VaultSnapshot(boolean exists, boolean locked, List<LoginInfo> logins, List<KeyInfo> keys, VaultSettings settings, Instant rememberedUntil, long revision, String deviceWarning)` with `record LoginInfo(UUID id, String name, String username, UUID keyId, boolean hasPassword)` and `record KeyInfo(UUID id, String name, String algorithm, String fingerprint, String publicKey, int loginUses)`.
- `record VaultSettings(int autoLockMinutes, int rememberDays)` with `VaultSettings.DEFAULT` = (15, 7).
- `CredentialMaterial implements AutoCloseable`: `username()`, `password()`, `privateKey()`, `passphrase()`.
- `interface DeviceAccessStore { boolean available(); byte[] read(String vaultId); void write(String vaultId, byte[] payload); void delete(String vaultId); }` and `DeviceAccessStores.system()`.

- [ ] **Step 1: Add dependencies**

In `jasper-app/build.gradle.kts`, replace the `dependencies` block with:

```kotlin
dependencies {
    implementation(project(":jasper-terminal"))
    implementation("org.tomlj:tomlj:1.1.1")
    implementation("net.java.dev.jna:jna:5.14.0")
    implementation("org.bouncycastle:bcprov-jdk18on:1.85.2")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.85")
    implementation("org.apache.sshd:sshd-common:2.18.0")
    runtimeOnly("org.slf4j:slf4j-nop:2.0.13")
}
```

- [ ] **Step 2: Copy the core with the package rename**

Run from the repository root:

```bash
set -e
B=codex/rail-vault-implementation
for kind in main test; do
  mkdir -p jasper-app/src/$kind/java/dev/jasper/app/vault
done
for f in CredentialMaterial DeviceAccessStore DeviceAccessStores DeviceAccessSupport LinuxDeviceAccessStore MacDeviceAccessStore NativeCredentialCalls VaultCrypto VaultData VaultFiles VaultKeys VaultService VaultSettings VaultSnapshot WindowsDeviceAccessStore; do
  git show $B:moray-app/src/main/java/dev/moray/app/vault/$f.java | sed 's/dev\.moray\.app/dev.jasper.app/g' > jasper-app/src/main/java/dev/jasper/app/vault/$f.java
done
for f in DeviceAccessStoresTest FakeCredentialCalls LinuxDeviceAccessStoreTest MacDeviceAccessStoreTest VaultServiceTest VaultStorageTest WindowsDeviceAccessStoreTest; do
  git show $B:moray-app/src/test/java/dev/moray/app/vault/$f.java | sed 's/dev\.moray\.app/dev.jasper.app/g' > jasper-app/src/test/java/dev/jasper/app/vault/$f.java
done
grep -rn "moray\|Moray" jasper-app/src/main/java/dev/jasper/app/vault jasper-app/src/test/java/dev/jasper/app/vault || true
```

The final grep must list only these four sites (plus any test that asserts them): `VaultCrypto.MAGIC`, `MacDeviceAccessStore` label string, `WindowsDeviceAccessStore` `new WString("Moray")`, `VaultFiles` temp prefix `.moray-vault-`.

- [ ] **Step 3: Apply the four brand fixes**

```bash
sed -i '' 's/0x4D4F524159564C54L/0x4A41535052564C54L/' jasper-app/src/main/java/dev/jasper/app/vault/VaultCrypto.java
sed -i '' 's/"Moray remembered vault access"/"Jasper remembered vault access"/' jasper-app/src/main/java/dev/jasper/app/vault/MacDeviceAccessStore.java
sed -i '' 's/new WString("Moray")/new WString("Jasper")/' jasper-app/src/main/java/dev/jasper/app/vault/WindowsDeviceAccessStore.java
sed -i '' 's/\.moray-vault-/.jasper-vault-/' jasper-app/src/main/java/dev/jasper/app/vault/VaultFiles.java
grep -rln "moray\|Moray" jasper-app/src/main/java/dev/jasper/app/vault jasper-app/src/test/java/dev/jasper/app/vault || echo clean
```

If a test asserted the old label, user name or prefix, update that literal to the Jasper value. If a test asserted the old magic bytes, update to `"JASPRVLT"`.

- [ ] **Step 4: Write the brand test**

`jasper-app/src/test/java/dev/jasper/app/vault/VaultBrandTest.java`:

```java
package dev.jasper.app.vault;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

class VaultBrandTest {
    @TempDir Path temp;

    @Test void newVaultFilesCarryTheJasperMagicAndSidecarName() throws Exception {
        Path file = temp.resolve("vault.enc");
        try (var service = new VaultService(file, new VaultServiceTest.MemoryStore(), Clock.systemUTC())) {
            service.create("synthetic master".toCharArray(), null);
        }
        byte[] header = Arrays.copyOf(Files.readAllBytes(file), 8);
        assertThat(new String(header, StandardCharsets.US_ASCII)).isEqualTo("JASPRVLT");
        assertThat(Files.list(temp).map(p -> p.getFileName().toString()))
            .containsExactlyInAnyOrder("vault.enc");
        try (var service = new VaultService(file, new VaultServiceTest.MemoryStore(), Clock.systemUTC())) {
            service.unlock("synthetic master".toCharArray(), java.time.Duration.ofDays(7));
        }
        assertThat(Files.exists(temp.resolve("vault.enc.device"))).isTrue();
    }
}
```

- [ ] **Step 5: Run the core tests**

```bash
./gradlew :jasper-app:test --tests 'dev.jasper.app.vault.*'
```

Expected: BUILD SUCCESSFUL. Read `jasper-app/build/test-results/test/TEST-dev.jasper.app.vault.*.xml`; every file reports `failures="0" errors="0"`. If `-Xlint:all` produces new warnings from ported files, fix them minimally (the branch compiled with the same flags, so none are expected).

- [ ] **Step 6: Run source hygiene and the whole check**

Run the Python snippet from `AGENTS.md` against `jasper-app/src`, then `./gradlew check`. Expected: BUILD SUCCESSFUL, no bad characters.

- [ ] **Step 7: Commit**

```bash
git add jasper-app/build.gradle.kts jasper-app/src/main/java/dev/jasper/app/vault jasper-app/src/test/java/dev/jasper/app/vault
git commit -m "feat: port the encrypted vault core to dev.jasper.app.vault

Copied from codex/rail-vault-implementation with the package rename,
Jasper file magic, keychain label, Windows user name and temp prefix.

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 2: Vault icons and the status-bar padlock

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/VaultIcons.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/WindowStatusBar.java`
- Create: `jasper-app/src/test/java/dev/jasper/app/VaultStatusIndicatorTest.java`

**Interfaces:**
- Produces: `static Icon VaultIcons.icon(String name)` for names `lock`, `unlock`, `login`, `key`, `eye`, `copy`, `import`, `settings`, `save`, `add`; every icon is 16×16 and paints in the component's foreground.
- Produces on `WindowStatusBar`: `Runnable onVaultClick` (field, default no-op); `void setVault(boolean connected, boolean unlocked, String tooltip)`; `JButton vaultButton()`.

- [ ] **Step 1: Write the failing test**

`jasper-app/src/test/java/dev/jasper/app/VaultStatusIndicatorTest.java`:

```java
package dev.jasper.app;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import static dev.jasper.app.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.assertThat;

class VaultStatusIndicatorTest {
    @Test void padlockReflectsStateAndStaysAtTheRightEdge() throws Exception {
        edt(() -> {
            var themes = new ThemeController();
            for (UiLookAndFeel laf : List.of(UiLookAndFeel.METAL, UiLookAndFeel.NIMBUS)) {
                themes.selectLaf(laf);
                var status = new WindowStatusBar();
                JButton padlock = status.vaultButton();
                assertThat(padlock.isEnabled()).isFalse();
                assertThat(padlock.getIcon()).isSameAs(VaultIcons.icon("lock"));
                assertThat(padlock.getToolTipText()).isEqualTo("Credential vault is not available");

                AtomicInteger clicks = new AtomicInteger();
                status.onVaultClick = clicks::incrementAndGet;
                status.setVault(true, true, "Credential vault unlocked. Click to lock.");
                assertThat(padlock.isEnabled()).isTrue();
                assertThat(padlock.getIcon()).isSameAs(VaultIcons.icon("unlock"));
                assertThat(padlock.getToolTipText()).isEqualTo("Credential vault unlocked. Click to lock.");
                assertThat(padlock.getAccessibleContext().getAccessibleName()).isEqualTo("Credential vault unlocked. Click to lock.");
                padlock.doClick();
                assertThat(clicks).hasValue(1);

                status.setVault(true, false, "Credential vault locked. Click to unlock.");
                assertThat(padlock.getIcon()).isSameAs(VaultIcons.icon("lock"));

                status.setMetadata("zsh", "/Users/example", "120 × 36", true);
                for (int width : new int[]{958, 320, 100, 40, 0}) {
                    status.setSize(width, 30); status.doLayout();
                    assertThat(padlock.getX() + padlock.getWidth()).isLessThanOrEqualTo(Math.max(width, padlock.getWidth()));
                    assertThat(padlock.getX()).isGreaterThanOrEqualTo(status.configButton().getParent().getX());
                    var image = new BufferedImage(Math.max(1, width), 30, BufferedImage.TYPE_INT_ARGB);
                    var g = image.createGraphics(); status.paint(g); g.dispose();
                }
            }
        });
    }

    @Test void everyIconIsSixteenPixelsAndPaintsInForeground() throws Exception {
        edt(() -> {
            for (String name : List.of("lock", "unlock", "login", "key", "eye", "copy", "import", "settings", "save", "add")) {
                Icon icon = VaultIcons.icon(name);
                assertThat(icon.getIconWidth()).isEqualTo(16);
                assertThat(icon.getIconHeight()).isEqualTo(16);
                var label = new JLabel(); label.setForeground(java.awt.Color.RED);
                var image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
                var g = image.createGraphics(); icon.paintIcon(label, g, 0, 0); g.dispose();
                boolean painted = false;
                for (int y = 0; y < 16 && !painted; y++) for (int x = 0; x < 16; x++)
                    if ((image.getRGB(x, y) >>> 24) != 0 && (image.getRGB(x, y) & 0xFF0000) != 0) { painted = true; break; }
                assertThat(painted).as(name).isTrue();
            }
            assertThat(VaultIcons.icon("lock")).isSameAs(VaultIcons.icon("lock"));
        });
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

```bash
./gradlew :jasper-app:test --tests 'dev.jasper.app.VaultStatusIndicatorTest'
```

Expected: compilation failure, `VaultIcons` and `vaultButton()` do not exist.

- [ ] **Step 3: Create `VaultIcons`**

`jasper-app/src/main/java/dev/jasper/app/VaultIcons.java`:

```java
package dev.jasper.app;

import java.awt.*;
import java.awt.geom.Path2D;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.swing.Icon;

/** Sixteen-pixel outline icons painted in the host component's foreground; no bundled artwork. */
final class VaultIcons {
    private static final Map<String, Icon> CACHE = new ConcurrentHashMap<>();

    private VaultIcons() {}

    static Icon icon(String name) {
        return CACHE.computeIfAbsent(name, VaultIcons::create);
    }

    private static Icon create(String name) {
        return new Icon() {
            @Override public int getIconWidth() { return 16; }
            @Override public int getIconHeight() { return 16; }
            @Override public void paintIcon(Component c, Graphics raw, int x, int y) {
                Graphics2D g = (Graphics2D) raw.create();
                try {
                    g.translate(x, y);
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g.setColor(c == null ? Color.DARK_GRAY : c.getForeground());
                    g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    paint(name, g);
                } finally { g.dispose(); }
            }
        };
    }

    private static void paint(String name, Graphics2D g) {
        switch (name) {
            case "lock" -> { g.drawRoundRect(3, 7, 10, 8, 2, 2); g.drawArc(5, 1, 6, 10, 0, 180); g.drawLine(8, 10, 8, 12); }
            case "unlock" -> { g.drawRoundRect(3, 7, 10, 8, 2, 2); g.drawArc(7, 1, 6, 10, 0, 180); g.drawLine(8, 10, 8, 12); }
            case "login" -> { g.drawOval(5, 1, 6, 6); g.drawArc(3, 9, 10, 9, 0, 180); }
            case "key" -> { g.drawOval(8, 1, 6, 6); g.drawLine(9, 6, 2, 13); g.drawLine(3, 12, 5, 14); g.drawLine(5, 10, 7, 12); }
            case "eye" -> { Path2D p = new Path2D.Float(); p.moveTo(1, 8); p.quadTo(8, -1, 15, 8); p.quadTo(8, 17, 1, 8); g.draw(p); g.drawOval(6, 6, 4, 4); }
            case "copy" -> { g.drawRoundRect(5, 5, 9, 10, 2, 2); g.drawPolyline(new int[]{11, 2, 2}, new int[]{2, 2, 12}, 3); }
            case "import" -> { g.drawLine(8, 2, 8, 11); g.drawPolyline(new int[]{4, 8, 12}, new int[]{6, 2, 6}, 3); g.drawPolyline(new int[]{2, 2, 14, 14}, new int[]{11, 14, 14, 11}, 4); }
            case "settings" -> {
                g.drawOval(3, 3, 10, 10); g.drawOval(6, 6, 4, 4);
                for (int a = 0; a < 8; a++) {
                    double t = a * Math.PI / 4;
                    g.drawLine((int) Math.round(8 + 5 * Math.cos(t)), (int) Math.round(8 + 5 * Math.sin(t)),
                        (int) Math.round(8 + 7 * Math.cos(t)), (int) Math.round(8 + 7 * Math.sin(t)));
                }
            }
            case "save" -> { g.drawRect(2, 2, 12, 12); g.drawRect(5, 2, 6, 4); g.drawRect(5, 9, 6, 5); }
            case "add" -> { g.drawLine(8, 2, 8, 14); g.drawLine(2, 8, 14, 8); }
            default -> throw new IllegalArgumentException("Unknown vault icon: " + name);
        }
    }
}
```

- [ ] **Step 4: Add the padlock to `WindowStatusBar`**

In `WindowStatusBar.java` add these fields after `private final Segment right = new Segment(configButton);`:

```java
    private final JButton vaultButton = new JButton(VaultIcons.icon("lock"));
    Runnable onVaultClick = () -> {};
    private static final int VAULT_WIDTH = 28;
```

In the constructor, before `add(left); add(right); refreshTheme();`:

```java
        vaultButton.setEnabled(false); vaultButton.setFocusable(false);
        vaultButton.setMargin(new Insets(0, 0, 0, 0)); vaultButton.setBorderPainted(false);
        vaultButton.setContentAreaFilled(false); vaultButton.putClientProperty("html.disable", true);
        vaultButton.setToolTipText("Credential vault is not available");
        vaultButton.getAccessibleContext().setAccessibleName("Credential vault is not available");
        vaultButton.addActionListener(event -> onVaultClick.run());
        add(vaultButton);
```

Add these methods next to `JButton configButton()`:

```java
    JButton vaultButton() { return vaultButton; }

    void setVault(boolean connected, boolean unlocked, String tooltip) {
        vaultButton.setEnabled(connected);
        vaultButton.setIcon(VaultIcons.icon(unlocked ? "unlock" : "lock"));
        vaultButton.setToolTipText(tooltip);
        vaultButton.getAccessibleContext().setAccessibleName(tooltip);
        revalidate(); repaint();
    }
```

In `refreshTheme()` add `vaultButton.setForeground(UIManager.getColor("Label.foreground"));` after the `configButton.setForeground(...)` line.

Replace `doLayout()` with:

```java
    @Override public void doLayout() {
        int edge = Math.min(6, getWidth() / 2), leftInset = edge;
        int vaultWidth = Math.min(VAULT_WIDTH, Math.max(0, getWidth() - edge));
        vaultButton.setBounds(Math.max(0, getWidth() - edge - vaultWidth), 0, vaultWidth, getHeight());
        int available = Math.max(0, vaultButton.getX() - edge * 2);
        int rightWidth = Math.min(available, right.getPreferredSize().width);
        right.setBounds(Math.max(edge, vaultButton.getX() - edge - rightWidth), 0, rightWidth, getHeight());
        left.setBounds(leftInset, 0, Math.max(0, right.getX() - leftInset - edge), getHeight());
    }
```

- [ ] **Step 5: Run the test and the existing status tests**

```bash
./gradlew :jasper-app:test --tests 'dev.jasper.app.VaultStatusIndicatorTest' --tests 'dev.jasper.app.ConfigurationStatusTest' --tests 'dev.jasper.app.WindowChromeTest'
```

Expected: BUILD SUCCESSFUL. If `ConfigurationStatusTest.bounded` fails because the padlock exceeds the bar at width 20 or 0, the `Math.min` clamps above are wrong; the padlock must shrink to the available width, never overflow.

- [ ] **Step 6: Hygiene, full check, commit**

```bash
./gradlew check
git add jasper-app/src/main/java/dev/jasper/app/VaultIcons.java jasper-app/src/main/java/dev/jasper/app/WindowStatusBar.java jasper-app/src/test/java/dev/jasper/app/VaultStatusIndicatorTest.java
git commit -m "feat: add the vault padlock to the status bar

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 3: Vault actions, Tools menu, palette commands and window hooks

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/ActionId.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/KeyBindings.java:29-36`
- Modify: `jasper-app/src/main/java/dev/jasper/app/WindowChrome.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/WindowCommands.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/WindowContent.java`
- Modify: `jasper-app/src/test/java/dev/jasper/app/KeyBindingsTest.java`
- Create: `jasper-app/src/test/java/dev/jasper/app/VaultChromeTest.java`

**Interfaces:**
- Consumes: `dev.jasper.app.vault.VaultSnapshot` (Task 1), `WindowStatusBar.setVault` (Task 2), `VaultIcons.icon` (Task 2).
- Produces on `WindowContent`: `void connectVault(Runnable openManager, Runnable toggleLock)`; `void disconnectVault()`; `void showVaultState(VaultSnapshot snapshot)`; `static String vaultTooltip(VaultSnapshot snapshot)`. New `ActionId.VAULT_MANAGER` (id `vault_manager`) and `ActionId.VAULT_LOCK` (id `vault_lock`). Menu bar index 5 is the Tools menu.

- [ ] **Step 1: Write the failing tests**

Add the two new actions to the catalog assertion in `KeyBindingsTest.actionCatalogCoversEveryPhaseOneActionAndUsesStableIds`: insert `ActionId.VAULT_MANAGER, ActionId.VAULT_LOCK,` immediately before `ActionId.QUIT`. In `everyPlatformDefaultHasOneUniqueActiveStrokePerAction` replace the loop body with:

```java
            for (ActionId action : ActionId.values()) {
                if (action.defaultBinding().equals("none")) {
                    assertThat(bindings.strokeFor(action)).as("no default for %s", action).isEmpty();
                    continue;
                }
                KeyStroke stroke = bindings.strokeFor(action).orElseThrow();
                assertThat(strokes.add(stroke)).as("unique %s binding for %s", macOs, action).isTrue();
                assertThat(bindings.actionFor(stroke)).contains(action);
            }
```

Create `jasper-app/src/test/java/dev/jasper/app/VaultChromeTest.java`:

```java
package dev.jasper.app;

import dev.jasper.app.vault.VaultSettings;
import dev.jasper.app.vault.VaultSnapshot;
import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import static dev.jasper.app.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

class VaultChromeTest {
    @org.junit.jupiter.api.AfterEach void cleanup() throws Exception { closeOwners(); }

    static VaultSnapshot snapshot(boolean exists, boolean locked, int autoLock) {
        return new VaultSnapshot(exists, locked, List.of(), List.of(), new VaultSettings(autoLock, 7), null, 1, null);
    }

    @Test void toolsMenuPaletteAndPadlockFollowTheVaultConnection() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            owner.updateActions();
            JMenu tools = owner.menuBar().getMenu(5);
            assertThat(tools.getText()).isEqualTo("Tools");
            assertThat(java.util.Arrays.stream(tools.getMenuComponents()).map(JMenuItem.class::cast).map(JMenuItem::getAction))
                .containsExactly(owner.action(ActionId.VAULT_MANAGER), owner.action(ActionId.VAULT_LOCK));
            assertThat(owner.action(ActionId.VAULT_MANAGER).isEnabled()).isFalse();
            assertThat(owner.action(ActionId.VAULT_LOCK).isEnabled()).isFalse();
            assertThat(owner.action(ActionId.VAULT_MANAGER).getValue(Action.ACCELERATOR_KEY)).isNull();
            assertThat(owner.status().vaultButton().isEnabled()).isFalse();

            AtomicInteger opened = new AtomicInteger(), toggled = new AtomicInteger();
            owner.connectVault(opened::incrementAndGet, toggled::incrementAndGet);
            owner.showVaultState(snapshot(true, true, 15));
            assertThat(owner.action(ActionId.VAULT_MANAGER).isEnabled()).isTrue();
            assertThat(owner.action(ActionId.VAULT_LOCK).getValue(Action.NAME)).isEqualTo("Lock Vault");
            assertThat(owner.action(ActionId.VAULT_LOCK).getValue(Command.TITLE)).isEqualTo("Lock Vault");
            assertThat(owner.status().vaultButton().isEnabled()).isTrue();
            assertThat(owner.status().vaultButton().getIcon()).isSameAs(VaultIcons.icon("unlock"));
            assertThat(owner.status().vaultButton().getToolTipText())
                .isEqualTo("Credential vault unlocked. Click to lock. Auto-lock after 15 min of inactivity.");

            owner.showVaultState(snapshot(true, false, 0));
            assertThat(owner.action(ActionId.VAULT_LOCK).getValue(Action.NAME)).isEqualTo("Unlock Vault…");
            assertThat(owner.status().vaultButton().getIcon()).isSameAs(VaultIcons.icon("lock"));
            assertThat(owner.status().vaultButton().getToolTipText()).isEqualTo("Credential vault locked. Click to unlock.");
            owner.showVaultState(snapshot(false, true, 15));
            assertThat(owner.action(ActionId.VAULT_LOCK).getValue(Action.NAME)).isEqualTo("Create Vault…");
            assertThat(owner.status().vaultButton().getToolTipText()).isEqualTo("No credential vault yet. Click to create one.");

            owner.invoke(ActionId.VAULT_MANAGER); owner.invoke(ActionId.VAULT_LOCK);
            owner.status().vaultButton().doClick();
            assertThat(opened).hasValue(1); assertThat(toggled).hasValue(2);

            var entries = owner.commands().entries();
            assertThat(CommandSearch.find(entries, "vault", List.of()).stream().map(Command::id))
                .contains("vault_manager", "vault_lock");
            assertThat(CommandSearch.find(entries, "credential", List.of()).stream().map(Command::id)).contains("vault_manager");
            assertThat(CommandSearch.find(entries, "unlock", List.of()).stream().map(Command::id)).contains("vault_lock");

            owner.disconnectVault();
            assertThat(owner.action(ActionId.VAULT_MANAGER).isEnabled()).isFalse();
            assertThat(owner.status().vaultButton().isEnabled()).isFalse();
            owner.close();
            assertThat(owner.action(ActionId.VAULT_LOCK).isEnabled()).isFalse();
        });
    }
}
```

- [ ] **Step 2: Run to verify failure**

```bash
./gradlew :jasper-app:test --tests 'dev.jasper.app.VaultChromeTest' --tests 'dev.jasper.app.KeyBindingsTest'
```

Expected: compilation failure on `ActionId.VAULT_MANAGER`.

- [ ] **Step 3: Add the actions and tolerate `none` defaults**

In `ActionId.java`, insert before `QUIT("quit", "Quit", "cmd+q");`:

```java
    VAULT_MANAGER("vault_manager", "Credential Vault…", "none"),
    VAULT_LOCK("vault_lock", "Lock Vault", "none"),
```

In `KeyBindings.defaults(boolean macOs)` replace the loop with:

```java
        for (ActionId action : ActionId.values()) {
            String binding = effectiveDefaultBinding(action, macOs);
            parse(binding, macOs).ifPresent(stroke -> putWithoutCollision(strokes, action, stroke, binding));
        }
```

- [ ] **Step 4: Add the Tools menu**

In `WindowChrome` constructor, after the `JMenu tab = menu("Tab", ...)` statement and before `menuBar.add(file); ...`, add:

```java
        JMenu tools = menu("Tools", ActionId.VAULT_MANAGER, ActionId.VAULT_LOCK);
```

and change the `menuBar.add` line to:

```java
        menuBar.add(file); menuBar.add(edit); menuBar.add(view); menuBar.add(pane); menuBar.add(tab); menuBar.add(tools);
```

- [ ] **Step 5: Register palette commands**

In `WindowCommands` constructor, extend the icon switch and keyword switch:

```java
            String icon = switch (id) {
                case NEW_TAB -> "square-plus"; case NEW_WINDOW -> "app-window";
                case SPLIT_RIGHT, SPLIT_DOWN -> "columns-2"; case ZOOM_PANE -> "maximize";
                case FIND, FIND_NEXT, FIND_PREVIOUS -> "search"; case OPEN_SETTINGS -> "settings";
                case RELOAD_CONFIG -> "refresh"; default -> null;
            };
            if (icon != null) owner.action(id).putValue(Command.ICON, AppIcons.icon(icon));
            if (id == ActionId.VAULT_MANAGER || id == ActionId.VAULT_LOCK)
                owner.action(id).putValue(Command.ICON, VaultIcons.icon("lock"));
```

and in the keywords switch add before `default -> List.of();`:

```java
                case VAULT_MANAGER -> List.of("vault", "credential", "credentials", "password", "ssh key", "manager");
                case VAULT_LOCK -> List.of("vault", "credential", "lock", "unlock", "create");
```

- [ ] **Step 6: Add the hooks to `WindowContent`**

Add the import `import dev.jasper.app.vault.VaultSnapshot;` and, next to `private Runnable openSettings, reloadConfiguration;`:

```java
    private Runnable openVaultManager, toggleVaultLock;
```

Add these methods after `disconnectConfiguration()`:

```java
    void connectVault(Runnable openManager, Runnable toggleLock) {
        if (closed) return;
        openVaultManager = Objects.requireNonNull(openManager); toggleVaultLock = Objects.requireNonNull(toggleLock);
        status().onVaultClick = () -> invoke(ActionId.VAULT_LOCK);
        updateActions();
    }

    void disconnectVault() {
        openVaultManager = null; toggleVaultLock = null;
        status().onVaultClick = () -> {};
        status().setVault(false, false, "Credential vault is not available");
        updateActions();
    }

    /** Metadata-only snapshot; never carries secrets. */
    void showVaultState(VaultSnapshot snapshot) {
        if (closed) return;
        String title = !snapshot.exists() ? "Create Vault…" : snapshot.locked() ? "Unlock Vault…" : "Lock Vault";
        action(ActionId.VAULT_LOCK).putValue(Action.NAME, title);
        action(ActionId.VAULT_LOCK).putValue(Command.TITLE, title);
        status().setVault(openVaultManager != null, snapshot.exists() && !snapshot.locked(), vaultTooltip(snapshot));
        updateActions();
    }

    static String vaultTooltip(VaultSnapshot snapshot) {
        if (!snapshot.exists()) return "No credential vault yet. Click to create one.";
        if (snapshot.locked()) return "Credential vault locked. Click to unlock.";
        int minutes = snapshot.settings().autoLockMinutes();
        return "Credential vault unlocked. Click to lock."
            + (minutes > 0 ? " Auto-lock after " + minutes + " min of inactivity." : "");
    }
```

In `updateActions()` add to the switch, before `default -> present;`:

```java
                    case VAULT_MANAGER, VAULT_LOCK -> openVaultManager != null;
```

In `invoke(ActionId id)` add to the switch after `case RELOAD_CONFIG -> reloadConfiguration.run();`:

```java
            case VAULT_MANAGER -> openVaultManager.run();
            case VAULT_LOCK -> toggleVaultLock.run();
```

In `close()`, after `unregisterConfiguration.run(); disconnectConfiguration();` add `disconnectVault();`.

- [ ] **Step 7: Run the tests**

```bash
./gradlew :jasper-app:test --tests 'dev.jasper.app.VaultChromeTest' --tests 'dev.jasper.app.KeyBindingsTest' --tests 'dev.jasper.app.ConfigTemplateTest' --tests 'dev.jasper.app.WindowCommandsTest' --tests 'dev.jasper.app.WindowChromeTest'
```

Expected: BUILD SUCCESSFUL. `ConfigTemplateTest` passes because the generated template now lists `# vault_manager = "none"` and parsing `none` yields no stroke, matching the defaults.

- [ ] **Step 8: Hygiene, full check, commit**

```bash
./gradlew check
git add jasper-app/src/main/java/dev/jasper/app/ActionId.java jasper-app/src/main/java/dev/jasper/app/KeyBindings.java jasper-app/src/main/java/dev/jasper/app/WindowChrome.java jasper-app/src/main/java/dev/jasper/app/WindowCommands.java jasper-app/src/main/java/dev/jasper/app/WindowContent.java jasper-app/src/test/java/dev/jasper/app/KeyBindingsTest.java jasper-app/src/test/java/dev/jasper/app/VaultChromeTest.java
git commit -m "feat: add vault actions, Tools menu and palette commands

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 4: Layout helpers and the editor forms

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/VaultUi.java`
- Create: `jasper-app/src/main/java/dev/jasper/app/VaultLoginForm.java`
- Create: `jasper-app/src/main/java/dev/jasper/app/VaultKeyForm.java`
- Create: `jasper-app/src/main/java/dev/jasper/app/VaultUnlockForm.java`
- Create: `jasper-app/src/main/java/dev/jasper/app/VaultAddLoginDialog.java`
- Create: `jasper-app/src/main/java/dev/jasper/app/VaultImportForm.java`
- Create: `jasper-app/src/main/java/dev/jasper/app/VaultSettingsForm.java`
- Create: `jasper-app/src/test/java/dev/jasper/app/VaultFormsTest.java`

**Interfaces:**
- Consumes: `VaultSnapshot`, `VaultSnapshot.LoginInfo`, `VaultSnapshot.KeyInfo`, `VaultSettings` (Task 1); `VaultIcons` (Task 2).
- Produces: `VaultUi` statics `action(String text, String icon, Runnable run)`, `row(Component...)`, `form()`, `field(JPanel, int row, String label, JComponent input)`, `pad(JComponent, int)`, `changes(JTextComponent, Runnable)`, `enabled(Component, boolean)`.
- `VaultLoginForm(boolean editor)`: fields `name`, `username`, `key` (JComboBox<KeyChoice>), `password`, `reveal`, `copy`, `reuse`; `Runnable changed`; `void keys(List<KeyInfo>, UUID selected)`; `void load(LoginInfo, List<KeyInfo>)`; `boolean dirty()`; `Draft draft()` where `record Draft(String name, String username, UUID keyId, char[] password) implements AutoCloseable`; `boolean hasTypedPassword()`; `void reveal(char[])`; `boolean revealed()`; `void hideSecret()`; `void clear()`.
- `VaultKeyForm()`: fields `name`, `algorithm`, `fingerprint`, `publicKey` (JTextArea), `copy`, `reuse`; `Runnable changed`; `void load(KeyInfo)`; `boolean dirty()`; `String draftName()`; `void clear()`.
- `VaultUnlockForm(boolean create, boolean rememberingAvailable)`: fields `password`, `confirmation`, `remember`, `message`, `expiry`, `primary`, `cancel`, `create`; `void state(VaultSnapshot)`; `void passwordVisible(boolean)`; `void showPassword(String reason)`; `void clear()`.
- `VaultAddLoginDialog(List<KeyInfo>)`: fields `form`, `imported`, `cancel`, `add`, `error`.
- `VaultImportForm()`: fields `name`, `passphrase`, `file`, `fingerprint`, `message`, `browse`, `inspect`, `save`, `cancel`, `Path source`; `void clear()`.
- `VaultSettingsForm(VaultSnapshot)`: fields `auto`, `days` (JSpinner), `forget`, `save`, `cancel`, `message`; `VaultSettings settings()`.
- `static String VaultUi.rememberedText(Instant until, Instant now)`.

- [ ] **Step 1: Write the failing tests**

`jasper-app/src/test/java/dev/jasper/app/VaultFormsTest.java`:

```java
package dev.jasper.app;

import dev.jasper.app.vault.VaultSettings;
import dev.jasper.app.vault.VaultSnapshot;
import java.awt.BorderLayout;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static dev.jasper.app.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.assertThat;

class VaultFormsTest {
    static final UUID LOGIN = UUID.fromString("10000000-0000-0000-0000-000000000001");
    static final UUID KEY = UUID.fromString("20000000-0000-0000-0000-000000000001");
    static final Instant NOW = Instant.parse("2026-09-14T09:00:00Z");

    static VaultSnapshot example(boolean locked) {
        return new VaultSnapshot(true, locked,
            locked ? List.of() : List.of(new VaultSnapshot.LoginInfo(LOGIN, "Production admin", "alice", KEY, true)),
            locked ? List.of() : List.of(new VaultSnapshot.KeyInfo(KEY, "Infrastructure", "Ed25519", "SHA256:fixture", "ssh-ed25519 AAAA fixture", 1)),
            VaultSettings.DEFAULT, null, 1, null);
    }

    @Test void loginDraftNeverLoadsTheSavedPasswordAndTracksDirtiness() throws Exception {
        edt(() -> {
            var form = new VaultLoginForm(true);
            form.load(example(false).logins().getFirst(), example(false).keys());
            assertThat(form.password.getPassword()).isEmpty();
            assertThat(form.password.getClientProperty("JTextField.placeholderText")).isEqualTo("Saved password");
            assertThat(form.key.getSelectedItem().toString()).isEqualTo("Infrastructure · Ed25519");
            assertThat(form.reuse.getText()).isEqualTo("Ed25519 · shared by 1 login");
            assertThat(form.dirty()).isFalse();
            form.name.setText("Renamed");
            assertThat(form.dirty()).isTrue();
            try (var draft = form.draft()) {
                assertThat(draft.name()).isEqualTo("Renamed");
                assertThat(draft.keyId()).isEqualTo(KEY);
                assertThat(draft.password()).isNull();
            }
            form.password.setText("typed secret");
            assertThat(form.hasTypedPassword()).isTrue();
            try (var draft = form.draft()) { assertThat(draft.password()).containsExactly("typed secret".toCharArray()); }
            form.reveal("shown".toCharArray());
            assertThat(form.revealed()).isTrue();
            form.hideSecret();
            assertThat(form.revealed()).isFalse();
            form.clear();
            assertThat(form.name.getText()).isEmpty();
            assertThat(form.password.getPassword()).isEmpty();
            assertThat(form.dirty()).isFalse();
            assertThat(form.password.getClientProperty("JTextField.placeholderText")).isEqualTo("Not set");
        });
    }

    @Test void keyFormShowsPublicDetailsOnlyAndTracksTheName() throws Exception {
        edt(() -> {
            var form = new VaultKeyForm();
            form.load(example(false).keys().getFirst());
            assertThat(form.algorithm.getText()).isEqualTo("Ed25519");
            assertThat(form.fingerprint.getText()).isEqualTo("SHA256:fixture");
            assertThat(form.publicKey.getText()).isEqualTo("ssh-ed25519 AAAA fixture");
            assertThat(form.publicKey.isEditable()).isFalse();
            assertThat(form.reuse.getText()).isEqualTo("Used by 1 login");
            assertThat(form.dirty()).isFalse();
            form.name.setText("Renamed key");
            assertThat(form.dirty()).isTrue();
            assertThat(form.draftName()).isEqualTo("Renamed key");
            form.clear();
            assertThat(form.publicKey.getText()).isEmpty();
        });
    }

    @Test void unlockFormSwitchesBetweenRememberedAndPasswordModes() throws Exception {
        edt(() -> {
            var create = new VaultUnlockForm(true, true);
            assertThat(create.primary.getText()).isEqualTo("Create vault");
            assertThat(create.confirmation.isVisible()).isTrue();
            var unlock = new VaultUnlockForm(false, true);
            assertThat(unlock.primary.getText()).isEqualTo("Unlock");
            unlock.state(new VaultSnapshot(true, true, List.of(), List.of(), VaultSettings.DEFAULT, NOW.plusSeconds(3600), 1, null));
            assertThat(unlock.remember.getText()).isEqualTo("Remember on this device for 7 days");
            unlock.passwordVisible(false);
            assertThat(unlock.password.isVisible()).isFalse();
            unlock.showPassword("Device access expired. Enter the master password.");
            assertThat(unlock.password.isVisible()).isTrue();
            assertThat(unlock.message.getText()).isEqualTo("Device access expired. Enter the master password.");
            unlock.password.setText("demo"); unlock.remember.setSelected(true); unlock.clear();
            assertThat(unlock.password.getPassword()).isEmpty();
            assertThat(unlock.remember.isSelected()).isFalse();
            var unavailable = new VaultUnlockForm(false, false);
            unavailable.state(new VaultSnapshot(true, true, List.of(), List.of(), VaultSettings.DEFAULT, null, 0, "OS credential store unavailable"));
            assertThat(unavailable.remember.isEnabled()).isFalse();
            assertThat(unavailable.message.getText()).isEqualTo("OS credential store unavailable. Use the master password.");
        });
    }

    @Test void settingsImportAndAddDialogExposeTheirControls() throws Exception {
        edt(() -> {
            var settings = new VaultSettingsForm(example(false));
            settings.auto.setValue(0); settings.days.setValue(365);
            assertThat(settings.settings()).isEqualTo(new VaultSettings(0, 365));
            var importForm = new VaultImportForm();
            assertThat(importForm.save.isEnabled()).isFalse();
            assertThat(importForm.file.getText()).isEqualTo("No file selected");
            importForm.passphrase.setText("x"); importForm.source = java.nio.file.Path.of("k"); importForm.clear();
            assertThat(importForm.passphrase.getPassword()).isEmpty();
            assertThat(importForm.source).isNull();
            var dialog = new VaultAddLoginDialog(example(false).keys());
            var layout = (BorderLayout) dialog.getLayout();
            assertThat(layout.getLayoutComponent(BorderLayout.CENTER)).isSameAs(dialog.form);
            var footer = (BorderLayout) dialog.footer.getLayout();
            assertThat(footer.getLayoutComponent(BorderLayout.WEST)).isSameAs(dialog.imported);
            assertThat(dialog.actions.getComponent(0)).isSameAs(dialog.cancel);
            assertThat(dialog.actions.getComponent(1)).isSameAs(dialog.add);
            assertThat(dialog.form.key.getItemCount()).isEqualTo(2);
            assertThat(dialog.form.key.getItemAt(0).toString()).isEqualTo("None");
        });
    }

    @Test void rememberedTextReportsMissingActiveAndExpiredAccess() {
        assertThat(VaultUi.rememberedText(null, NOW)).isEqualTo("No remembered device access");
        assertThat(VaultUi.rememberedText(NOW.plusSeconds(60), NOW)).startsWith("Device access until ");
        assertThat(VaultUi.rememberedText(NOW.minusSeconds(60), NOW)).startsWith("Device access expired ");
    }
}
```

- [ ] **Step 2: Run to verify failure**

```bash
./gradlew :jasper-app:test --tests 'dev.jasper.app.VaultFormsTest'
```

Expected: compilation failure (classes missing).

- [ ] **Step 3: Create `VaultUi`**

```java
package dev.jasper.app;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.JTextComponent;

/** Small Swing layout helpers shared by the vault forms; plain logical pixels, no scaling library. */
final class VaultUi {
    private VaultUi() {}

    static Action action(String text, String icon, Runnable run) {
        return new AbstractAction(text, icon == null ? null : VaultIcons.icon(icon)) {
            @Override public void actionPerformed(ActionEvent e) { if (isEnabled()) run.run(); }
        };
    }

    static JPanel row(Component... children) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        for (Component child : children) panel.add(child);
        return panel;
    }

    static JPanel form() { return new JPanel(new GridBagLayout()); }

    static void field(JPanel panel, int row, String text, JComponent input) {
        JLabel label = new JLabel(text); label.setLabelFor(input);
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0; c.gridy = row; c.anchor = GridBagConstraints.WEST; c.insets = new Insets(5, 0, 5, 14);
        panel.add(label, c);
        c.gridx = 1; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL; c.insets = new Insets(5, 0, 5, 0);
        panel.add(input, c);
        if (!text.isBlank()) input.getAccessibleContext().setAccessibleName(text);
    }

    static void pad(JComponent component, int pixels) {
        component.setBorder(BorderFactory.createEmptyBorder(pixels, pixels, pixels, pixels));
    }

    static void changes(JTextComponent input, Runnable changed) {
        input.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { changed.run(); }
            @Override public void removeUpdate(DocumentEvent e) { changed.run(); }
            @Override public void changedUpdate(DocumentEvent e) { changed.run(); }
        });
    }

    static void enabled(Component component, boolean value) {
        component.setEnabled(value);
        if (component instanceof Container container)
            for (Component child : container.getComponents()) enabled(child, value);
    }

    static String rememberedText(Instant until, Instant now) {
        if (until == null) return "No remembered device access";
        String formatted = DateTimeFormatter.ofPattern("MMM d, yyyy 'at' h:mm a").withZone(ZoneId.systemDefault()).format(until);
        return (until.isAfter(now) ? "Device access until " : "Device access expired ") + formatted;
    }
}
```

- [ ] **Step 4: Create `VaultLoginForm`**

```java
package dev.jasper.app;

import dev.jasper.app.vault.VaultSnapshot;
import java.awt.*;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import javax.swing.*;

/** Login draft editor. The committed password is never loaded; only typed text becomes a draft value. */
final class VaultLoginForm extends JPanel {
    record KeyChoice(UUID id, String label) { @Override public String toString() { return label; } }
    record Draft(String name, String username, UUID keyId, char[] password) implements AutoCloseable {
        @Override public void close() { if (password != null) Arrays.fill(password, (char) 0); }
        @Override public String toString() { return "LoginDraft[redacted]"; }
    }

    final JTextField name = new JTextField(20), username = new JTextField(20);
    final JComboBox<KeyChoice> key = new JComboBox<>();
    final JPasswordField password = new JPasswordField(20);
    final JLabel reuse = new JLabel(" ");
    final JButton reveal = new JButton(VaultIcons.icon("eye")), copy = new JButton(VaultIcons.icon("copy"));
    Runnable changed = () -> {};
    private final char defaultEcho = password.getEchoChar();
    private boolean loading, passwordChanged, dirty;

    VaultLoginForm(boolean editor) {
        super(new BorderLayout(0, 6));
        JPanel fields = VaultUi.form();
        VaultUi.field(fields, 0, editor ? "Name" : "Login name", name);
        VaultUi.field(fields, 1, "Username", username);
        VaultUi.field(fields, 2, "SSH key", key);
        JPanel secret = new JPanel(new BorderLayout(4, 0)); secret.add(password);
        if (editor) secret.add(VaultUi.row(reveal, copy), BorderLayout.EAST);
        VaultUi.field(fields, 3, "Password", secret);
        VaultUi.field(fields, 4, "", reuse);
        add(fields, BorderLayout.NORTH);
        reveal.setToolTipText("Reveal or hide password"); reveal.getAccessibleContext().setAccessibleName("Reveal or hide password");
        copy.setToolTipText("Copy password"); copy.getAccessibleContext().setAccessibleName("Copy password");
        password.getAccessibleContext().setAccessibleName("Password");
        VaultUi.changes(name, this::mark); VaultUi.changes(username, this::mark);
        VaultUi.changes(password, () -> { if (!loading) passwordChanged = true; mark(); });
        key.addActionListener(e -> mark());
        setMinimumSize(new Dimension(0, 0));
        load(null, List.of());
    }

    private void mark() { if (!loading) { dirty = true; changed.run(); } }

    void keys(List<VaultSnapshot.KeyInfo> keys, UUID selected) {
        boolean before = loading; loading = true;
        key.removeAllItems(); key.addItem(new KeyChoice(null, "None"));
        for (var k : keys) key.addItem(new KeyChoice(k.id(), k.name() + " · " + k.algorithm()));
        choose(selected); loading = before;
    }

    void choose(UUID id) {
        for (int i = 0; i < key.getItemCount(); i++)
            if (Objects.equals(key.getItemAt(i).id(), id)) { key.setSelectedIndex(i); return; }
        key.setSelectedIndex(0);
    }

    void load(VaultSnapshot.LoginInfo login, List<VaultSnapshot.KeyInfo> keys) {
        loading = true;
        name.setText(login == null ? "" : login.name()); username.setText(login == null ? "" : login.username());
        keys(keys, login == null ? null : login.keyId());
        password.setText(""); password.setEchoChar(defaultEcho);
        password.putClientProperty("JTextField.placeholderText", login != null && login.hasPassword() ? "Saved password" : "Not set");
        reuse.setText(keys.stream().filter(k -> login != null && k.id().equals(login.keyId())).findFirst()
            .map(k -> k.algorithm() + " · shared by " + k.loginUses() + " login" + (k.loginUses() == 1 ? "" : "s")).orElse(" "));
        passwordChanged = false; dirty = false; loading = false;
    }

    boolean dirty() { return dirty; }
    boolean hasTypedPassword() { return passwordChanged; }
    Draft draft() {
        KeyChoice choice = (KeyChoice) key.getSelectedItem();
        return new Draft(name.getText().strip(), username.getText().strip(), choice == null ? null : choice.id(),
            passwordChanged ? password.getPassword() : null);
    }
    void reveal(char[] secret) { loading = true; password.setText(new String(secret)); password.setEchoChar((char) 0); loading = false; }
    boolean revealed() { return password.getEchoChar() == 0; }
    void hideSecret() { loading = true; if (!passwordChanged) password.setText(""); password.setEchoChar(defaultEcho); loading = false; }
    void clear() { load(null, List.of()); }
}
```

- [ ] **Step 5: Create `VaultKeyForm`**

```java
package dev.jasper.app;

import dev.jasper.app.vault.VaultSnapshot;
import java.awt.*;
import javax.swing.*;

/** Key details editor: only the name is editable; private material is never shown here. */
final class VaultKeyForm extends JPanel {
    final JTextField name = new JTextField(20);
    final JLabel algorithm = new JLabel(" "), fingerprint = new JLabel(" "), reuse = new JLabel(" ");
    final JTextArea publicKey = new JTextArea(3, 40);
    final JButton copy = new JButton(VaultIcons.icon("copy"));
    Runnable changed = () -> {};
    private boolean loading, dirty;

    VaultKeyForm() {
        super(new BorderLayout(0, 6));
        JPanel fields = VaultUi.form();
        VaultUi.field(fields, 0, "Name", name);
        VaultUi.field(fields, 1, "Algorithm", algorithm);
        VaultUi.field(fields, 2, "Fingerprint", fingerprint);
        publicKey.setEditable(false); publicKey.setLineWrap(true); publicKey.setWrapStyleWord(true);
        JPanel pub = new JPanel(new BorderLayout(4, 0));
        JScrollPane scroll = new JScrollPane(publicKey); scroll.setPreferredSize(new Dimension(0, 64));
        pub.add(scroll); pub.add(VaultUi.row(copy), BorderLayout.EAST);
        VaultUi.field(fields, 3, "Public key", pub);
        VaultUi.field(fields, 4, "", reuse);
        add(fields, BorderLayout.NORTH);
        copy.setToolTipText("Copy public key"); copy.getAccessibleContext().setAccessibleName("Copy public key");
        publicKey.getAccessibleContext().setAccessibleName("Public key");
        VaultUi.changes(name, () -> { if (!loading) { dirty = true; changed.run(); } });
        setMinimumSize(new Dimension(0, 0));
    }

    void load(VaultSnapshot.KeyInfo key) {
        loading = true;
        name.setText(key == null ? "" : key.name());
        algorithm.setText(key == null ? " " : key.algorithm());
        fingerprint.setText(key == null ? " " : key.fingerprint());
        publicKey.setText(key == null ? "" : key.publicKey()); publicKey.setCaretPosition(0);
        reuse.setText(key == null ? " " : "Used by " + key.loginUses() + " login" + (key.loginUses() == 1 ? "" : "s"));
        dirty = false; loading = false;
    }

    boolean dirty() { return dirty; }
    String draftName() { return name.getText().strip(); }
    void clear() { load(null); }
}
```

- [ ] **Step 6: Create `VaultUnlockForm`**

```java
package dev.jasper.app;

import dev.jasper.app.vault.VaultSnapshot;
import java.awt.*;
import java.time.Instant;
import javax.swing.*;

/** Create or unlock the vault. Shown inline in a locked manager and as a dialog from the padlock/menu. */
final class VaultUnlockForm extends JPanel {
    final JPasswordField password = new JPasswordField(24), confirmation = new JPasswordField(24);
    final JCheckBox remember = new JCheckBox("Remember on this device");
    final JLabel message = new JLabel(" "), expiry = new JLabel(" ");
    final JButton primary = new JButton(), cancel = new JButton("Cancel");
    final boolean create;
    private final JPanel fields = VaultUi.form();
    private final JLabel passwordLabel;
    private final boolean rememberingAvailable;

    VaultUnlockForm(boolean create, boolean rememberingAvailable) {
        super(new BorderLayout(0, 12));
        this.create = create; this.rememberingAvailable = rememberingAvailable;
        VaultUi.pad(this, 24);
        JLabel title = new JLabel(create ? "Create vault" : "Vault locked");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
        add(title, BorderLayout.NORTH);
        VaultUi.field(fields, 0, "Master password", password);
        passwordLabel = (JLabel) fields.getComponent(0);
        VaultUi.field(fields, 1, "Confirm password", confirmation);
        confirmation.setVisible(create); fields.getComponent(2).setVisible(create);
        VaultUi.field(fields, 2, "", remember);
        VaultUi.field(fields, 3, "", expiry);
        VaultUi.field(fields, 4, "", message);
        add(fields);
        primary.setText(create ? "Create vault" : "Unlock");
        add(VaultUi.row(primary, cancel), BorderLayout.SOUTH);
        remember.setEnabled(rememberingAvailable);
        password.getAccessibleContext().setAccessibleName("Master password");
        setPreferredSize(new Dimension(480, 290));
    }

    void state(VaultSnapshot snapshot) {
        expiry.setText(VaultUi.rememberedText(snapshot.rememberedUntil(), Instant.now()));
        remember.setText("Remember on this device for " + snapshot.settings().rememberDays() + " days");
        boolean usable = rememberingAvailable && (snapshot.deviceWarning() == null || snapshot.deviceWarning().isBlank());
        remember.setEnabled(usable);
        if (!usable) { remember.setSelected(false); message.setText("OS credential store unavailable. Use the master password."); }
    }

    void passwordVisible(boolean visible) {
        passwordLabel.setVisible(visible); password.setVisible(visible); remember.setVisible(visible);
        revalidate(); repaint();
    }

    void showPassword(String reason) { passwordVisible(true); message.setText(reason); password.requestFocusInWindow(); }

    void clear() { password.setText(""); confirmation.setText(""); remember.setSelected(false); }
}
```

- [ ] **Step 7: Create `VaultAddLoginDialog`, `VaultImportForm`, `VaultSettingsForm`**

`VaultAddLoginDialog.java`:

```java
package dev.jasper.app;

import dev.jasper.app.vault.VaultSnapshot;
import java.awt.BorderLayout;
import java.util.List;
import javax.swing.*;

/** Body of the Add credential dialog: the supplied mock's form with Import at bottom left. */
final class VaultAddLoginDialog extends JPanel {
    final VaultLoginForm form = new VaultLoginForm(false);
    final JButton imported = new JButton("Import SSH key…");
    final JButton cancel = new JButton("Cancel");
    final JButton add = new JButton("Add login");
    final JLabel error = new JLabel(" ");
    final JPanel footer = new JPanel(new BorderLayout());
    final JPanel actions = VaultUi.row(cancel, add);

    VaultAddLoginDialog(List<VaultSnapshot.KeyInfo> keys) {
        super(new BorderLayout(0, 18));
        VaultUi.pad(this, 20);
        form.load(null, keys);
        add(form, BorderLayout.CENTER);
        footer.add(error, BorderLayout.NORTH);
        footer.add(imported, BorderLayout.WEST);
        footer.add(actions, BorderLayout.EAST);
        add(footer, BorderLayout.SOUTH);
    }
}
```

`VaultImportForm.java`:

```java
package dev.jasper.app;

import java.awt.*;
import java.nio.file.Path;
import javax.swing.*;

/** Import an SSH private key: choose, inspect (fingerprint preview), then import an encrypted copy. */
final class VaultImportForm extends JPanel {
    final JTextField name = new JTextField(22);
    final JPasswordField passphrase = new JPasswordField(22);
    final JLabel file = new JLabel("No file selected"), fingerprint = new JLabel(" ");
    final JLabel message = new JLabel("Select a private key to inspect it.");
    final JButton browse = new JButton("Choose file…"), inspect = new JButton("Inspect key");
    final JButton save = new JButton("Import key"), cancel = new JButton("Cancel");
    Path source;

    VaultImportForm() {
        super(new BorderLayout(0, 12));
        VaultUi.pad(this, 20);
        JPanel fields = VaultUi.form();
        VaultUi.field(fields, 0, "Key name", name);
        VaultUi.field(fields, 1, "Private key", VaultUi.row(file, browse));
        VaultUi.field(fields, 2, "Passphrase (if encrypted)", passphrase);
        VaultUi.field(fields, 3, "Fingerprint", fingerprint);
        VaultUi.field(fields, 4, "", message);
        add(fields);
        add(VaultUi.row(inspect, cancel, save), BorderLayout.SOUTH);
        save.setEnabled(false);
        setPreferredSize(new Dimension(600, 300));
    }

    void clear() { passphrase.setText(""); source = null; }
}
```

`VaultSettingsForm.java`:

```java
package dev.jasper.app;

import dev.jasper.app.vault.VaultSettings;
import dev.jasper.app.vault.VaultSnapshot;
import java.awt.*;
import java.time.Instant;
import javax.swing.*;

/** Vault settings live inside the encrypted vault; this form edits them and can forget device access. */
final class VaultSettingsForm extends JPanel {
    final JSpinner auto, days;
    final JButton forget = new JButton("Forget this device"), save = new JButton("Save"), cancel = new JButton("Cancel");
    final JLabel message = new JLabel(" ");

    VaultSettingsForm(VaultSnapshot snapshot) {
        super(new BorderLayout(0, 16));
        VaultUi.pad(this, 20);
        auto = new JSpinner(new SpinnerNumberModel(snapshot.settings().autoLockMinutes(), 0, 1440, 1));
        days = new JSpinner(new SpinnerNumberModel(snapshot.settings().rememberDays(), 1, 365, 1));
        JPanel fields = VaultUi.form();
        VaultUi.field(fields, 0, "Auto-lock minutes (0 disables)", auto);
        VaultUi.field(fields, 1, "Remember days", days);
        VaultUi.field(fields, 2, "Device access", new JLabel(VaultUi.rememberedText(snapshot.rememberedUntil(), Instant.now())));
        VaultUi.field(fields, 3, "", forget);
        VaultUi.field(fields, 4, "", new JLabel("Duration changes do not renew current device access."));
        VaultUi.field(fields, 5, "", message);
        add(fields);
        add(VaultUi.row(cancel, save), BorderLayout.SOUTH);
        setPreferredSize(new Dimension(510, 280));
    }

    VaultSettings settings() { return new VaultSettings((Integer) auto.getValue(), (Integer) days.getValue()); }
}
```

- [ ] **Step 8: Run the tests**

```bash
./gradlew :jasper-app:test --tests 'dev.jasper.app.VaultFormsTest'
```

Expected: BUILD SUCCESSFUL, 5 tests passed.

- [ ] **Step 9: Hygiene, full check, commit**

```bash
./gradlew check
git add jasper-app/src/main/java/dev/jasper/app/VaultUi.java jasper-app/src/main/java/dev/jasper/app/VaultLoginForm.java jasper-app/src/main/java/dev/jasper/app/VaultKeyForm.java jasper-app/src/main/java/dev/jasper/app/VaultUnlockForm.java jasper-app/src/main/java/dev/jasper/app/VaultAddLoginDialog.java jasper-app/src/main/java/dev/jasper/app/VaultImportForm.java jasper-app/src/main/java/dev/jasper/app/VaultSettingsForm.java jasper-app/src/test/java/dev/jasper/app/VaultFormsTest.java
git commit -m "feat: add credential vault editor forms

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 5: The manager panel

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/VaultManagerPanel.java`
- Create: `jasper-app/src/test/java/dev/jasper/app/VaultManagerPanelTest.java`

**Interfaces:**
- Consumes: `VaultSnapshot` (Task 1), `VaultIcons` (Task 2), `VaultUi`, `VaultLoginForm`, `VaultKeyForm` (Task 4).
- Produces `VaultManagerPanel extends JPanel`: fields `search` (JTextField), `sidebar` (JList<Entry>), `table` (JTable), `login` (VaultLoginForm), `keyForm` (VaultKeyForm), `title`, `kind`, `message`, `status`, `expiry` (JLabel), `saveButton`, `revertButton`, `deleteButton`, `addTop`, `importTop`, `settingsTop`, `lockTop` (JButton), actions `addAction`, `importAction`, `settingsAction`, `lockAction`, `saveAction`, `revertAction`, `deleteAction`; callbacks `Runnable onAdd, onImport, onSettings, onLock, onSave, onRevert, onDelete`; `Consumer<Runnable> navigate`; `enum Entry { VAULT_HEADER, ALL, LOGINS, KEYS, MANAGEMENT_HEADER, SETTINGS, IMPORT }`; methods `void showSnapshot(VaultSnapshot)`, `void lockedContent(JComponent)`, `void select(UUID)`, `UUID selected()`, `boolean dirty()`, `void refreshButtons(boolean busy)`, `void restoreSearch()`, `VaultSnapshot.LoginInfo findLogin(UUID)`, `VaultSnapshot.KeyInfo findKey(UUID)`, `Entry category()`.

- [ ] **Step 1: Write the failing tests**

`jasper-app/src/test/java/dev/jasper/app/VaultManagerPanelTest.java`:

```java
package dev.jasper.app;

import dev.jasper.app.vault.VaultSettings;
import dev.jasper.app.vault.VaultSnapshot;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import static dev.jasper.app.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.assertThat;

class VaultManagerPanelTest {
    static final UUID LOGIN = VaultFormsTest.LOGIN, KEY = VaultFormsTest.KEY;
    static final UUID OTHER = UUID.fromString("10000000-0000-0000-0000-000000000002");

    static VaultSnapshot two() {
        return new VaultSnapshot(true, false,
            List.of(new VaultSnapshot.LoginInfo(LOGIN, "Production admin", "alice", KEY, false),
                new VaultSnapshot.LoginInfo(OTHER, "Backup", "bob", null, true)),
            List.of(new VaultSnapshot.KeyInfo(KEY, "Infrastructure", "Ed25519", "SHA256:fixture", "ssh-ed25519 AAAA", 1)),
            new VaultSettings(15, 7), null, 2, null);
    }

    static void layout(Container c) { c.doLayout(); for (Component child : c.getComponents()) if (child instanceof Container nested) layout(nested); }

    @Test void snapshotFillsSidebarCountsTableAndEditorsAndLockClearsThem() throws Exception {
        edt(() -> {
            var panel = new VaultManagerPanel();
            panel.showSnapshot(two());
            assertThat(panel.sidebar.getModel().getSize()).isEqualTo(7);
            assertThat(panel.sidebar.getSelectedValue()).isEqualTo(VaultManagerPanel.Entry.ALL);
            var renderer = panel.sidebar.getCellRenderer();
            assertThat(((JLabel) renderer.getListCellRendererComponent(panel.sidebar, VaultManagerPanel.Entry.ALL, 1, false, false)).getText())
                .isEqualTo("All credentials  3");
            assertThat(((JLabel) renderer.getListCellRendererComponent(panel.sidebar, VaultManagerPanel.Entry.LOGINS, 2, false, false)).getText())
                .isEqualTo("Logins  2");
            assertThat(((JLabel) renderer.getListCellRendererComponent(panel.sidebar, VaultManagerPanel.Entry.KEYS, 3, false, false)).getText())
                .isEqualTo("SSH keys  1");
            assertThat(panel.table.getRowCount()).isEqualTo(3);
            assertThat(panel.table.getValueAt(0, 0)).isEqualTo("Backup");
            assertThat(panel.table.getValueAt(0, 2)).isEqualTo("Password");
            assertThat(panel.table.getValueAt(1, 0)).isEqualTo("Infrastructure");
            assertThat(panel.table.getValueAt(1, 2)).isEqualTo("Ed25519");
            assertThat(panel.table.getValueAt(2, 2)).isEqualTo("SSH key");
            assertThat(panel.status.getText()).isEqualTo("3 credentials    Auto-lock 15 min");
            assertThat(panel.expiry.getText()).isEqualTo("No remembered device access");
            assertThat(panel.selected()).isEqualTo(OTHER);
            assertThat(panel.title.getText()).isEqualTo("Backup");
            assertThat(panel.kind.getText()).isEqualTo("Login");
            assertThat(panel.login.isShowing() || panel.login.getParent() != null).isTrue();

            panel.select(KEY);
            assertThat(panel.kind.getText()).isEqualTo("SSH key");
            assertThat(panel.keyForm.getParent()).isNotNull();
            assertThat(panel.login.getParent()).isNull();
            panel.keyForm.name.setText("Renamed");
            assertThat(panel.dirty()).isTrue();
            assertThat(panel.saveAction.isEnabled()).isTrue();

            panel.showSnapshot(new VaultSnapshot(true, true, List.of(), List.of(), new VaultSettings(0, 7), null, 3, null));
            assertThat(panel.table.getRowCount()).isZero();
            assertThat(panel.dirty()).isFalse();
            assertThat(panel.keyForm.name.getText()).isEmpty();
            assertThat(panel.login.password.getPassword()).isEmpty();
            assertThat(panel.status.getText()).isEqualTo("0 credentials    Auto-lock off");
            assertThat(panel.addAction.isEnabled()).isFalse();
            assertThat(panel.lockAction.isEnabled()).isTrue();
            var form = new JLabel("unlock form");
            panel.lockedContent(form);
            assertThat(form.getParent()).isNotNull();
        });
    }

    @Test void sidebarFiltersRunManagementActionsAndDefersThroughNavigate() throws Exception {
        edt(() -> {
            var panel = new VaultManagerPanel();
            panel.showSnapshot(two());
            var pending = new ArrayList<Runnable>(); panel.navigate = pending::add;
            panel.sidebar.setSelectedValue(VaultManagerPanel.Entry.LOGINS, false);
            assertThat(panel.category()).isEqualTo(VaultManagerPanel.Entry.ALL);
            assertThat(panel.sidebar.getSelectedValue()).isEqualTo(VaultManagerPanel.Entry.ALL);
            pending.removeFirst().run();
            assertThat(panel.category()).isEqualTo(VaultManagerPanel.Entry.LOGINS);
            assertThat(panel.sidebar.getSelectedValue()).isEqualTo(VaultManagerPanel.Entry.LOGINS);
            assertThat(panel.table.getRowCount()).isEqualTo(2);
            panel.navigate = Runnable::run;
            panel.sidebar.setSelectedValue(VaultManagerPanel.Entry.KEYS, false);
            assertThat(panel.table.getRowCount()).isEqualTo(1);
            assertThat(panel.selected()).isEqualTo(KEY);
            panel.sidebar.setSelectedValue(VaultManagerPanel.Entry.VAULT_HEADER, false);
            assertThat(panel.sidebar.getSelectedValue()).isEqualTo(VaultManagerPanel.Entry.KEYS);

            AtomicInteger settings = new AtomicInteger(), imports = new AtomicInteger();
            panel.onSettings = settings::incrementAndGet; panel.onImport = imports::incrementAndGet;
            panel.sidebar.setSelectedValue(VaultManagerPanel.Entry.SETTINGS, false);
            assertThat(settings).hasValue(1);
            assertThat(panel.sidebar.getSelectedValue()).isEqualTo(VaultManagerPanel.Entry.KEYS);
            panel.sidebar.setSelectedValue(VaultManagerPanel.Entry.IMPORT, false);
            assertThat(imports).hasValue(1);
            panel.importTop.doClick(); assertThat(imports).hasValue(2);
            panel.settingsTop.doClick(); assertThat(settings).hasValue(2);

            panel.sidebar.setSelectedValue(VaultManagerPanel.Entry.ALL, false);
            panel.search.setText("prod");
        });
        edt(() -> {});
        edt(() -> {});
    }

    @Test void searchFiltersNameAndUsernameAfterTheEventQueueSettles() throws Exception {
        VaultManagerPanel[] holder = new VaultManagerPanel[1];
        edt(() -> { holder[0] = new VaultManagerPanel(); holder[0].showSnapshot(two()); holder[0].search.setText("bob"); });
        edt(() -> {}); edt(() -> {});
        edt(() -> {
            assertThat(holder[0].table.getRowCount()).isEqualTo(1);
            assertThat(holder[0].table.getValueAt(0, 0)).isEqualTo("Backup");
            holder[0].search.setText("ed25519");
        });
        edt(() -> {}); edt(() -> {});
        edt(() -> {
            assertThat(holder[0].table.getRowCount()).isEqualTo(1);
            assertThat(holder[0].table.getValueAt(0, 0)).isEqualTo("Infrastructure");
            holder[0].search.setText("nothing here");
        });
        edt(() -> {}); edt(() -> {});
        edt(() -> {
            assertThat(holder[0].table.getRowCount()).isZero();
            assertThat(holder[0].selected()).isNull();
            assertThat(holder[0].title.getText()).isEqualTo("Select a credential");
        });
    }

    @Test void tableSitsAboveTheEditorAndSaveStaysReachableWhenNarrow() throws Exception {
        edt(() -> {
            var themes = new ThemeController();
            for (UiLookAndFeel laf : List.of(UiLookAndFeel.METAL, UiLookAndFeel.MOTIF, UiLookAndFeel.NIMBUS)) {
                themes.selectLaf(laf);
                var panel = new VaultManagerPanel();
                panel.showSnapshot(two()); panel.select(LOGIN);
                panel.setSize(900, 600); layout(panel);
                assertThat(SwingUtilities.convertPoint(panel.table, 0, 0, panel).y)
                    .isLessThan(SwingUtilities.convertPoint(panel.login, 0, 0, panel).y);
                assertThat(SwingUtilities.convertPoint(panel.sidebar, 0, 0, panel).x)
                    .isLessThan(SwingUtilities.convertPoint(panel.table, 0, 0, panel).x);
                assertThat(panel.settingsTop.getAction()).isSameAs(panel.settingsAction);
                assertThat(panel.lockTop.getAction()).isSameAs(panel.lockAction);
                panel.setSize(560, 420); layout(panel);
                assertThat(panel.saveButton.getWidth()).isPositive();
                assertThat(SwingUtilities.convertPoint(panel.saveButton, 0, 0, panel).y + panel.saveButton.getHeight())
                    .isLessThanOrEqualTo(panel.getHeight());
                var image = new java.awt.image.BufferedImage(560, 420, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                var g = image.createGraphics(); panel.paint(g); g.dispose();
            }
        });
    }
}
```

- [ ] **Step 2: Run to verify failure**

```bash
./gradlew :jasper-app:test --tests 'dev.jasper.app.VaultManagerPanelTest'
```

Expected: compilation failure, `VaultManagerPanel` missing.

- [ ] **Step 3: Create `VaultManagerPanel`**

```java
package dev.jasper.app;

import dev.jasper.app.vault.VaultSnapshot;
import java.awt.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;

/** Manager contents in standard Swing: toolbar row, sidebar list, table over editor, status strip. */
final class VaultManagerPanel extends JPanel {
    enum Entry {
        VAULT_HEADER("VAULT"), ALL("All credentials"), LOGINS("Logins"), KEYS("SSH keys"),
        MANAGEMENT_HEADER("MANAGEMENT"), SETTINGS("Settings"), IMPORT("Import key");
        final String label;
        Entry(String label) { this.label = label; }
        boolean header() { return this == VAULT_HEADER || this == MANAGEMENT_HEADER; }
        boolean category() { return this == ALL || this == LOGINS || this == KEYS; }
    }

    final JTextField search = new JTextField(20);
    final JList<Entry> sidebar = new JList<>(Entry.values());
    final JTable table = new JTable();
    final VaultLoginForm login = new VaultLoginForm(true);
    final VaultKeyForm keyForm = new VaultKeyForm();
    final JLabel title = new JLabel("Select a credential"), kind = new JLabel(" ");
    final JLabel message = new JLabel(" "), status = new JLabel(" "), expiry = new JLabel(" ");
    final Action addAction = VaultUi.action("Add credential", "add", () -> onAdd.run());
    final Action importAction = VaultUi.action("Import key", "import", () -> onImport.run());
    final Action settingsAction = VaultUi.action("Settings", "settings", () -> onSettings.run());
    final Action lockAction = VaultUi.action("Lock vault", "lock", () -> onLock.run());
    final Action saveAction = VaultUi.action("Save", "save", () -> onSave.run());
    final Action revertAction = VaultUi.action("Revert", null, () -> onRevert.run());
    final Action deleteAction = VaultUi.action("Delete", null, () -> onDelete.run());
    final JButton addTop = new JButton(addAction), importTop = new JButton(importAction);
    final JButton settingsTop = new JButton(settingsAction), lockTop = new JButton(lockAction);
    final JButton saveButton = new JButton(saveAction), revertButton = new JButton(revertAction), deleteButton = new JButton(deleteAction);
    Runnable onAdd = () -> {}, onImport = () -> {}, onSettings = () -> {}, onLock = () -> {};
    Runnable onSave = () -> {}, onRevert = () -> {}, onDelete = () -> {};
    Consumer<Runnable> navigate = Runnable::run;

    private final JPanel body = new JPanel(new BorderLayout());
    private final JSplitPane split;
    private final JPanel editor = new JPanel(new BorderLayout());
    private final List<UUID> rows = new ArrayList<>();
    private VaultSnapshot snapshot;
    private UUID selected;
    private Entry category = Entry.ALL;
    private String filter = "", acceptedSearch = "";
    private boolean loading;
    private final AbstractTableModel model = new AbstractTableModel() {
        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return 3; }
        @Override public String getColumnName(int column) { return new String[]{"Name", "Username", "Authentication"}[column]; }
        @Override public Object getValueAt(int row, int column) {
            UUID id = rows.get(row); var l = findLogin(id); var k = findKey(id);
            return switch (column) {
                case 0 -> l != null ? l.name() : k.name();
                case 1 -> l != null ? l.username() : "";
                default -> l != null ? (l.keyId() != null ? (l.hasPassword() ? "SSH key + Password" : "SSH key") : "Password") : k.algorithm();
            };
        }
    };

    VaultManagerPanel() {
        super(new BorderLayout());
        setPreferredSize(new Dimension(900, 600)); setMinimumSize(new Dimension(0, 0));
        search.putClientProperty("JTextField.placeholderText", "Search credentials");
        search.getAccessibleContext().setAccessibleName("Search credentials");
        settingsTop.setText(""); settingsTop.setToolTipText("Vault settings");
        settingsTop.getAccessibleContext().setAccessibleName("Vault settings");
        JPanel toolbar = new JPanel(new BorderLayout()); VaultUi.pad(toolbar, 8);
        toolbar.add(VaultUi.row(search, addTop, importTop));
        toolbar.add(VaultUi.row(settingsTop, lockTop), BorderLayout.EAST);
        add(toolbar, BorderLayout.NORTH);

        sidebar.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        sidebar.setCellRenderer(new SidebarRenderer());
        sidebar.setFixedCellHeight(28);
        sidebar.getAccessibleContext().setAccessibleName("Vault navigation");
        sidebar.setSelectedValue(Entry.ALL, false);
        sidebar.addListSelectionListener(e -> { if (!loading && !e.getValueIsAdjusting()) sidebarSelected(); });
        JScrollPane side = new JScrollPane(sidebar); side.setPreferredSize(new Dimension(160, 0)); side.setMinimumSize(new Dimension(120, 0));

        table.setModel(model);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(28); table.setShowVerticalLines(false); table.setFillsViewportHeight(true);
        table.getAccessibleContext().setAccessibleName("Credentials");
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable t, Object value, boolean isSelected, boolean focus, int row, int column) {
                JLabel label = (JLabel) super.getTableCellRendererComponent(t, value, isSelected, focus, row, column);
                label.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 6));
                label.setIcon(column == 0 ? VaultIcons.icon(findLogin(rows.get(row)) != null ? "login" : "key") : null);
                return label;
            }
        });
        table.getSelectionModel().addListSelectionListener(e -> {
            if (loading || e.getValueIsAdjusting() || table.getSelectedRow() < 0) return;
            UUID id = rows.get(table.getSelectedRow());
            loading = true; restoreSelection(); loading = false;
            if (!Objects.equals(id, selected)) navigate.accept(() -> select(id));
        });
        JScrollPane list = new JScrollPane(table); list.setMinimumSize(new Dimension(0, 80));

        JPanel detail = new JPanel(new BorderLayout(0, 10)); VaultUi.pad(detail, 12);
        JPanel heading = new JPanel(new BorderLayout());
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
        heading.add(title); heading.add(VaultUi.row(kind, deleteButton), BorderLayout.EAST);
        detail.add(heading, BorderLayout.NORTH);
        JScrollPane editorScroll = new JScrollPane(editor); editorScroll.setBorder(BorderFactory.createEmptyBorder());
        editorScroll.setMinimumSize(new Dimension(0, 0));
        detail.add(editorScroll);
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(message); bottom.add(VaultUi.row(revertButton, saveButton), BorderLayout.EAST);
        detail.add(bottom, BorderLayout.SOUTH);
        detail.setMinimumSize(new Dimension(0, 160));

        JSplitPane vertical = new JSplitPane(JSplitPane.VERTICAL_SPLIT, list, detail);
        vertical.setResizeWeight(0.4); vertical.setBorder(BorderFactory.createEmptyBorder());
        split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, side, vertical);
        split.setResizeWeight(0); split.setBorder(BorderFactory.createEmptyBorder());
        body.add(split); add(body);

        JPanel footer = new JPanel(new BorderLayout()); VaultUi.pad(footer, 8);
        footer.add(status); footer.add(expiry, BorderLayout.EAST); add(footer, BorderLayout.SOUTH);

        VaultUi.changes(search, () -> {
            if (loading) return;
            String text = search.getText();
            SwingUtilities.invokeLater(() -> {
                if (loading || !search.getText().equals(text)) return;
                navigate.accept(() -> { acceptedSearch = text; filter = text.toLowerCase(Locale.ROOT).strip(); rebuild(); });
            });
        });
        login.changed = () -> refreshButtons(false);
        keyForm.changed = () -> refreshButtons(false);
        refreshButtons(false);
    }

    private void sidebarSelected() {
        Entry entry = sidebar.getSelectedValue();
        if (entry == null) return;
        if (entry.header()) { syncSidebar(); return; }
        if (entry == Entry.SETTINGS) { syncSidebar(); onSettings.run(); return; }
        if (entry == Entry.IMPORT) { syncSidebar(); onImport.run(); return; }
        syncSidebar();
        navigate.accept(() -> { category = entry; syncSidebar(); rebuild(); });
    }

    private void syncSidebar() {
        boolean before = loading; loading = true;
        sidebar.setSelectedValue(category, false);
        loading = before;
    }

    void showSnapshot(VaultSnapshot s) {
        snapshot = s;
        int total = s.logins().size() + s.keys().size();
        status.setText(total + " credentials    " + (s.settings().autoLockMinutes() == 0 ? "Auto-lock off" : "Auto-lock " + s.settings().autoLockMinutes() + " min"));
        expiry.setText(VaultUi.rememberedText(s.rememberedUntil(), Instant.now()));
        sidebar.repaint();
        if (s.locked()) {
            loading = true; selected = null; rows.clear(); model.fireTableDataChanged();
            login.clear(); keyForm.clear(); editor.removeAll();
            title.setText("Vault locked"); title.setIcon(null); kind.setText(" "); loading = false;
        } else {
            body.removeAll(); body.add(split); rebuild();
        }
        refreshButtons(false); revalidate(); repaint();
    }

    void lockedContent(JComponent form) { body.removeAll(); body.add(form); revalidate(); repaint(); }

    private void rebuild() {
        if (snapshot == null || snapshot.locked()) return;
        loading = true; syncSidebar(); rows.clear();
        List<Object[]> entries = new ArrayList<>();
        if (category != Entry.KEYS) for (var l : snapshot.logins())
            if ((l.name() + " " + l.username()).toLowerCase(Locale.ROOT).contains(filter)) entries.add(new Object[]{l.name(), l.id()});
        if (category != Entry.LOGINS) for (var k : snapshot.keys())
            if ((k.name() + " " + k.algorithm() + " " + k.fingerprint()).toLowerCase(Locale.ROOT).contains(filter)) entries.add(new Object[]{k.name(), k.id()});
        entries.sort(Comparator.comparing(e -> ((String) e[0]).toLowerCase(Locale.ROOT)));
        for (Object[] e : entries) rows.add((UUID) e[1]);
        model.fireTableDataChanged(); loading = false;
        select(rows.contains(selected) ? selected : rows.isEmpty() ? null : rows.getFirst());
    }

    void select(UUID id) {
        loading = true; selected = id; restoreSelection(); editor.removeAll();
        var l = findLogin(id); var k = findKey(id);
        if (l != null) {
            title.setText(l.name()); title.setIcon(VaultIcons.icon("login")); kind.setText("Login");
            login.load(l, snapshot.keys()); keyForm.clear(); editor.add(login, BorderLayout.NORTH);
        } else if (k != null) {
            title.setText(k.name()); title.setIcon(VaultIcons.icon("key")); kind.setText("SSH key");
            keyForm.load(k); login.clear(); editor.add(keyForm, BorderLayout.NORTH);
        } else {
            login.clear(); keyForm.clear(); title.setText("Select a credential"); title.setIcon(null); kind.setText(" ");
        }
        loading = false; refreshButtons(false); editor.revalidate(); editor.repaint();
    }

    private void restoreSelection() {
        int row = rows.indexOf(selected);
        if (row < 0) table.clearSelection(); else table.setRowSelectionInterval(row, row);
    }

    VaultSnapshot.LoginInfo findLogin(UUID id) {
        return snapshot == null || id == null ? null : snapshot.logins().stream().filter(l -> l.id().equals(id)).findFirst().orElse(null);
    }
    VaultSnapshot.KeyInfo findKey(UUID id) {
        return snapshot == null || id == null ? null : snapshot.keys().stream().filter(k -> k.id().equals(id)).findFirst().orElse(null);
    }
    void restoreSearch() { loading = true; search.setText(acceptedSearch); syncSidebar(); loading = false; }
    UUID selected() { return selected; }
    Entry category() { return category; }
    boolean dirty() { return login.dirty() || keyForm.dirty(); }

    void refreshButtons(boolean busy) {
        boolean open = snapshot != null && !snapshot.locked();
        saveAction.setEnabled(open && !busy && dirty()); revertAction.setEnabled(open && !busy && dirty());
        deleteAction.setEnabled(open && !busy && selected != null);
        addAction.setEnabled(open && !busy); importAction.setEnabled(open && !busy); settingsAction.setEnabled(open && !busy);
        lockAction.setEnabled(snapshot != null && snapshot.exists() && !busy);
        table.setEnabled(!busy); search.setEnabled(!busy); sidebar.setEnabled(!busy);
        VaultUi.enabled(editor, !busy);
    }

    private final class SidebarRenderer extends DefaultListCellRenderer {
        @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            Entry entry = (Entry) value;
            String text = entry.label;
            if (snapshot != null && entry.category()) {
                int count = switch (entry) {
                    case ALL -> snapshot.logins().size() + snapshot.keys().size();
                    case LOGINS -> snapshot.logins().size();
                    default -> snapshot.keys().size();
                };
                text = entry.label + "  " + count;
            }
            JLabel label = (JLabel) super.getListCellRendererComponent(list, text, index, isSelected && !entry.header(), cellHasFocus);
            label.setBorder(BorderFactory.createEmptyBorder(0, entry.header() ? 8 : 14, 0, 8));
            label.setFont(label.getFont().deriveFont(entry.header() ? Font.BOLD : Font.PLAIN, entry.header() ? 10f : label.getFont().getSize2D()));
            label.setEnabled(!entry.header());
            label.setIcon(switch (entry) {
                case ALL -> VaultIcons.icon("unlock"); case LOGINS -> VaultIcons.icon("login"); case KEYS -> VaultIcons.icon("key");
                case SETTINGS -> VaultIcons.icon("settings"); case IMPORT -> VaultIcons.icon("import"); default -> null;
            });
            return label;
        }
    }
}
```

- [ ] **Step 4: Run the tests**

```bash
./gradlew :jasper-app:test --tests 'dev.jasper.app.VaultManagerPanelTest'
```

Expected: BUILD SUCCESSFUL, 4 tests passed. If the narrow-width assertion fails, lower `detail`'s minimum height or the table scroll pane's minimum size; the editor must scroll before Save leaves the panel.

- [ ] **Step 5: Hygiene, full check, commit**

```bash
./gradlew check
git add jasper-app/src/main/java/dev/jasper/app/VaultManagerPanel.java jasper-app/src/test/java/dev/jasper/app/VaultManagerPanelTest.java
git commit -m "feat: add the credential manager panel

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 6: Activity tracking and the controller

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/VaultActivity.java`
- Create: `jasper-app/src/main/java/dev/jasper/app/VaultController.java`
- Create: `jasper-app/src/test/java/dev/jasper/app/VaultActivityTest.java`
- Create: `jasper-app/src/test/java/dev/jasper/app/VaultControllerTest.java`

**Interfaces:**
- Consumes: `VaultService`, `VaultSnapshot`, `VaultSettings`, `CredentialMaterial`, `DeviceAccessStore` (Task 1); `VaultManagerPanel` (Task 5); forms (Task 4).
- Produces `VaultActivity implements AutoCloseable`: `VaultActivity(Supplier<VaultService>, Consumer<Boolean> lockedNow, boolean start)`; `void start()`; `AutoCloseable register(Component root)`; `int rootCount()`; `void event(AWTEvent)`; `void tick()`; `void close()`.
- Produces `VaultController implements AutoCloseable`: constructors `VaultController(Callable<VaultService> factory)`, `VaultController(VaultService service, Executor worker, Clipboard clipboard)`, `VaultController(Callable<VaultService> factory, Executor worker, Clipboard clipboard)`; fields `panel`, `activity`, `unlockForm`, `Consumer<Dialog> present`, `Runnable showManager`, `Runnable hideManager`, `Supplier<Path> chooseFile`; nested `static final class Dialog { String title; JComponent content; JButton primary; JComponent initial; Runnable cancel; Runnable dispose, cleanup; }`; methods `VaultSnapshot snapshot()`, `AutoCloseable onChange(Runnable)`, `void openManager()`, `void toggleLock()`, `void lock(boolean explicit)`, `void requestClose()`, `void cancelDialog(Dialog)`, `void startActivity()`, `CredentialMaterial resolveLogin(UUID)`, `CredentialMaterial resolveKey(UUID, String)`, `void close()`; `static String sanitized(Exception, String fallback)`.

- [ ] **Step 1: Write the failing activity test**

`jasper-app/src/test/java/dev/jasper/app/VaultActivityTest.java`:

```java
package dev.jasper.app;

import dev.jasper.app.vault.VaultService;
import dev.jasper.app.vault.VaultSettings;
import java.awt.event.*;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicLong;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static dev.jasper.app.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.assertThat;

class VaultActivityTest {
    @TempDir Path temp;

    @Test void inputInsideRegisteredRootsCountsButOutputForeignInputAndResizesDoNot() throws Exception {
        AtomicLong nanos = new AtomicLong();
        try (var service = new VaultService(temp.resolve("v.enc"), new VaultControllerTest.Store(), Clock.systemUTC(), nanos::get)) {
            service.create("synthetic master".toCharArray(), null);
            service.saveSettings(new VaultSettings(1, 7));
            edt(() -> {
                var locks = new ArrayList<Boolean>();
                var activity = new VaultActivity(() -> service, locks::add, false);
                JPanel a = new JPanel(), b = new JPanel(), foreign = new JPanel();
                activity.register(a); activity.register(b);
                nanos.set(50_000_000_000L);
                activity.event(new KeyEvent(a, KeyEvent.KEY_PRESSED, 0, 0, KeyEvent.VK_A, 'a'));
                nanos.set(100_000_000_000L);
                activity.event(new MouseEvent(b, MouseEvent.MOUSE_PRESSED, 0, 0, 1, 1, 1, false));
                activity.tick();
                assertThat(service.snapshot().locked()).isFalse();
                nanos.set(150_000_000_000L);
                activity.event(new KeyEvent(foreign, KeyEvent.KEY_PRESSED, 0, 0, KeyEvent.VK_A, 'a'));
                activity.event(new ComponentEvent(a, ComponentEvent.COMPONENT_RESIZED));
                nanos.set(160_000_000_000L);
                activity.tick();
                assertThat(service.snapshot().locked()).isTrue();
                activity.tick();
                assertThat(locks).containsExactly(false, true, false);
                activity.close();
                assertThat(activity.rootCount()).isZero();
            });
        }
    }

    @Test void menuItemsUnderARegisteredRootPaneCountAndUnregisterStops() throws Exception {
        AtomicLong nanos = new AtomicLong();
        try (var service = new VaultService(temp.resolve("m.enc"), new VaultControllerTest.Store(), Clock.systemUTC(), nanos::get)) {
            service.create("synthetic master".toCharArray(), null);
            service.saveSettings(new VaultSettings(1, 7));
            edt(() -> {
                var activity = new VaultActivity(() -> service, locked -> {}, false);
                var root = new JRootPane(); var bar = new JMenuBar(); var menu = new JMenu("Tools");
                var item = new JMenuItem("Vault"); menu.add(item); bar.add(menu); root.setJMenuBar(bar);
                AutoCloseable registration = activity.register(root);
                nanos.set(50_000_000_000L);
                activity.event(new KeyEvent(item, KeyEvent.KEY_PRESSED, 0, 0, KeyEvent.VK_ENTER, '\n'));
                nanos.set(100_000_000_000L);
                activity.tick();
                assertThat(service.snapshot().locked()).isFalse();
                try { registration.close(); } catch (Exception e) { throw new AssertionError(e); }
                activity.event(new KeyEvent(item, KeyEvent.KEY_PRESSED, 0, 0, KeyEvent.VK_ENTER, '\n'));
                nanos.set(110_000_000_000L);
                activity.tick();
                assertThat(service.snapshot().locked()).isTrue();
                activity.close();
            });
        }
    }
}
```

- [ ] **Step 2: Write the failing controller tests**

`jasper-app/src/test/java/dev/jasper/app/VaultControllerTest.java`:

```java
package dev.jasper.app;

import dev.jasper.app.vault.*;
import java.awt.Container;
import java.awt.Component;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static dev.jasper.app.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.assertThat;

class VaultControllerTest {
    @TempDir Path temp;

    static final class Store implements DeviceAccessStore {
        int reads; byte[] payload; boolean fail; boolean available = true;
        @Override public boolean available() { return available; }
        @Override public byte[] read(String id) throws IOException {
            reads++; if (fail) throw new IOException("secret error payload");
            return payload == null ? null : payload.clone();
        }
        @Override public void write(String id, byte[] p) { payload = p.clone(); }
        @Override public void delete(String id) { payload = null; }
    }

    static final class Jobs implements Executor {
        final Queue<Runnable> queue = new ArrayDeque<>();
        @Override public void execute(Runnable r) { queue.add(r); }
        void drain() throws Exception { while (!queue.isEmpty()) { queue.remove().run(); edt(() -> {}); } }
    }

    static JButton button(Container root, String text) {
        for (Component c : root.getComponents()) {
            if (c instanceof JButton b && text.equals(b.getText())) return b;
            if (c instanceof Container nested) { var found = button(nested, text); if (found != null) return found; }
        }
        return null;
    }

    VaultService service(Store store) throws IOException {
        return new VaultService(temp.resolve("v.enc"), store, Clock.systemUTC());
    }

    @Test void startupStaysLockedRememberedErrorFallsBackToPasswordWithoutLeakingText() throws Exception {
        Store store = new Store();
        try (var service = service(store)) {
            service.create("synthetic master".toCharArray(), Duration.ofDays(7));
            service.lock(VaultService.LockReason.AUTO);
            store.fail = true;
            Jobs jobs = new Jobs(); VaultController[] c = new VaultController[1];
            edt(() -> {
                c[0] = new VaultController(service, jobs, new Clipboard("test"));
                c[0].openManager(); c[0].openManager();
                assertThat(store.reads).isZero();
                assertThat(c[0].unlockForm.password.isVisible()).isFalse();
                c[0].unlockForm.primary.doClick();
            });
            jobs.drain();
            edt(() -> {
                assertThat(c[0].unlockForm.password.isVisible()).isTrue();
                assertThat(c[0].unlockForm.message.getText()).doesNotContain("secret error payload");
                assertThat(service.snapshot().locked()).isTrue();
                c[0].unlockForm.password.setText("synthetic master"); c[0].unlockForm.primary.doClick();
            });
            jobs.drain();
            edt(() -> { assertThat(service.snapshot().locked()).isFalse(); assertThat(c[0].panel.table.getRowCount()).isZero(); c[0].close(); });
            jobs.drain();
        }
    }

    @Test void toggleLockPromptsADialogWhenLockedAndSealsWhenUnlocked() throws Exception {
        Jobs jobs = new Jobs(); List<VaultController.Dialog> shown = new ArrayList<>();
        try (var service = service(new Store())) {
            VaultController[] c = new VaultController[1]; AtomicInteger changes = new AtomicInteger();
            edt(() -> {
                c[0] = new VaultController(service, jobs, new Clipboard("test"));
                c[0].present = shown::add; c[0].onChange(changes::incrementAndGet);
                c[0].toggleLock();
                assertThat(shown).hasSize(1);
                assertThat(shown.getFirst().title).isEqualTo("Create vault");
                var form = (VaultUnlockForm) shown.getFirst().content;
                form.password.setText("synthetic master"); form.confirmation.setText("synthetic master");
                form.primary.doClick();
            });
            jobs.drain();
            edt(() -> {
                assertThat(service.snapshot().locked()).isFalse();
                assertThat(changes.get()).isPositive();
                c[0].toggleLock();
                assertThat(service.snapshot().locked()).isTrue();
                c[0].toggleLock();
                assertThat(shown).hasSize(2);
                assertThat(shown.getLast().title).isEqualTo("Unlock vault");
                shown.getLast().cancel.run();
                c[0].close();
            });
            jobs.drain();
        }
    }

    @Test void staleCreateCannotRepopulateALockedManager() throws Exception {
        Jobs jobs = new Jobs();
        try (var service = service(new Store())) {
            VaultController[] c = new VaultController[1];
            edt(() -> {
                c[0] = new VaultController(service, jobs, new Clipboard("test")); c[0].openManager();
                c[0].unlockForm.password.setText("synthetic master"); c[0].unlockForm.confirmation.setText("synthetic master");
                c[0].unlockForm.primary.doClick();
                c[0].lock(false);
            });
            jobs.drain();
            edt(() -> {
                assertThat(service.snapshot().locked()).isTrue();
                assertThat(c[0].panel.login.password.getPassword()).isEmpty();
                assertThat(c[0].panel.table.getRowCount()).isZero();
                c[0].close();
            });
            jobs.drain();
        }
    }

    @Test void saveRevertConflictAndDeleteGuards() throws Exception {
        Jobs jobs = new Jobs(); List<VaultController.Dialog> shown = new ArrayList<>();
        try (var service = service(new Store())) {
            service.create("synthetic master".toCharArray(), null);
            UUID keyId = service.importKey("Infra", VaultServiceTest.syntheticKey("Ed25519"), new char[0]).id();
            UUID id = service.saveLogin(null, "Original", "alice", "demo".toCharArray(), keyId);
            VaultController[] c = new VaultController[1];
            edt(() -> {
                c[0] = new VaultController(service, jobs, new Clipboard("test")); c[0].present = shown::add;
                c[0].panel.select(id);
                c[0].panel.login.name.setText("Rename"); c[0].panel.revertButton.doClick();
                assertThat(c[0].panel.login.name.getText()).isEqualTo("Original");
                c[0].panel.login.name.setText("Saved"); c[0].panel.saveButton.doClick();
            });
            jobs.drain();
            assertThat(service.snapshot().logins().getFirst().name()).isEqualTo("Saved");
            edt(() -> { c[0].panel.select(keyId); c[0].panel.deleteButton.doClick(); });
            jobs.drain();
            edt(() -> {
                assertThat(shown).isEmpty();
                assertThat(c[0].panel.message.getText()).isEqualTo("This key is used by 1 login. Change those logins first.");
                assertThat(service.snapshot().keys()).hasSize(1);
                c[0].panel.select(id); c[0].panel.deleteButton.doClick();
                assertThat(shown.getLast().title).isEqualTo("Delete credential");
                button(shown.getLast().content, "Delete").doClick();
            });
            jobs.drain();
            assertThat(service.snapshot().logins()).isEmpty();
            Files.write(temp.resolve("v.enc"), new byte[]{1, 2, 3});
            edt(() -> { c[0].panel.select(keyId); c[0].panel.keyForm.name.setText("Unsaved"); c[0].panel.saveButton.doClick(); });
            jobs.drain();
            edt(() -> {
                assertThat(c[0].panel.keyForm.name.getText()).isEqualTo("Unsaved");
                assertThat(c[0].panel.dirty()).isTrue();
                assertThat(c[0].panel.message.getText()).contains("Vault changed externally");
                assertThat(c[0].snapshot().keys().getFirst().name()).isEqualTo("Infra");
                c[0].close();
            });
            jobs.drain();
        }
    }

    @Test void addLoginCopyPasswordAndDirtyNavigationUseRealControls() throws Exception {
        Jobs jobs = new Jobs(); Store store = new Store();
        try (var service = service(store)) {
            service.create("synthetic master".toCharArray(), null);
            UUID first = service.saveLogin(null, "First", "alice", "secret".toCharArray(), null);
            UUID second = service.saveLogin(null, "Second", "bob", "other".toCharArray(), null);
            Clipboard clipboard = new Clipboard("test"); VaultController[] c = new VaultController[1];
            List<VaultController.Dialog> shown = new ArrayList<>();
            edt(() -> {
                c[0] = new VaultController(service, jobs, clipboard); c[0].present = shown::add;
                c[0].panel.select(first); c[0].panel.login.copy.doClick();
            });
            jobs.drain();
            assertThat(clipboard.getData(DataFlavor.stringFlavor)).isEqualTo("secret");
            edt(() -> {
                c[0].panel.login.name.setText("Draft");
                c[0].panel.navigate.accept(() -> c[0].panel.select(second));
                assertThat(shown.getLast().title).isEqualTo("Unsaved credential changes");
                shown.getLast().cancel.run();
                assertThat(c[0].panel.selected()).isEqualTo(first);
                assertThat(c[0].panel.login.name.getText()).isEqualTo("Draft");
                c[0].panel.revertButton.doClick();
                c[0].panel.addTop.doClick();
                VaultController.Dialog add = shown.getLast();
                assertThat(add.title).isEqualTo("Add credential");
                var body = (VaultAddLoginDialog) add.content;
                body.form.name.setText("Third"); body.form.username.setText("carol"); body.form.password.setText("pw");
                body.add.doClick();
            });
            jobs.drain();
            edt(() -> {
                assertThat(service.snapshot().logins()).extracting(VaultSnapshot.LoginInfo::name).contains("Third");
                assertThat(c[0].panel.title.getText()).isEqualTo("Third");
                c[0].close();
            });
            jobs.drain();
        }
    }

    @Test void importFromAddPreviewsFingerprintAndSurvivesSourceRemoval() throws Exception {
        Path source = temp.resolve("synthetic.key");
        Files.write(source, VaultServiceTest.syntheticKey("Ed25519"));
        Jobs jobs = new Jobs();
        try (var service = service(new Store())) {
            service.create("synthetic master".toCharArray(), null);
            VaultController[] c = new VaultController[1]; List<VaultController.Dialog> shown = new ArrayList<>();
            edt(() -> {
                c[0] = new VaultController(service, jobs, new Clipboard("test")); c[0].present = shown::add; c[0].chooseFile = () -> source;
                c[0].panel.addTop.doClick();
                var add = (VaultAddLoginDialog) shown.getLast().content;
                add.form.name.setText("New login"); add.form.username.setText("alice"); add.form.password.setText("draft secret");
                add.imported.doClick();
                var form = (VaultImportForm) shown.getLast().content;
                form.browse.doClick(); form.inspect.doClick();
            });
            jobs.drain();
            edt(() -> {
                var form = (VaultImportForm) shown.getLast().content;
                assertThat(form.fingerprint.getText()).startsWith("Ed25519 · SHA256:");
                assertThat(form.save.isEnabled()).isTrue();
                form.save.doClick();
            });
            jobs.drain();
            Files.delete(source);
            edt(() -> {
                var add = (VaultAddLoginDialog) shown.getFirst().content;
                assertThat(add.form.key.getSelectedItem().toString()).startsWith("synthetic.key");
                assertThat(add.form.name.getText()).isEqualTo("New login");
                assertThat(add.form.password.getPassword()).containsExactly("draft secret".toCharArray());
                add.add.doClick();
            });
            jobs.drain();
            UUID login = service.snapshot().logins().getFirst().id();
            try (var material = service.resolveLogin(login)) {
                assertThat(material.privateKey()).isNotEmpty();
                assertThat(material.password()).containsExactly("draft secret".toCharArray());
            }
            edt(() -> c[0].close()); jobs.drain();
        }
    }

    @Test void sanitizedMessagesOnlyPassKnownCoreText() {
        assertThat(VaultController.sanitized(new IOException("Vault changed externally; lock and unlock again before saving"), "fallback"))
            .isEqualTo("Vault changed externally; lock and unlock again before saving");
        assertThat(VaultController.sanitized(new IOException("secret error payload"), "fallback")).isEqualTo("fallback");
        assertThat(VaultController.sanitized(new IllegalStateException("Vault locked"), "fallback")).isEqualTo("fallback");
    }
}
```

- [ ] **Step 3: Run to verify failure**

```bash
./gradlew :jasper-app:test --tests 'dev.jasper.app.VaultActivityTest' --tests 'dev.jasper.app.VaultControllerTest'
```

Expected: compilation failure (classes missing). `VaultServiceTest.syntheticKey` and `VaultServiceTest.MemoryStore` must be package-visible statics in the ported test; if they are `private`, remove the modifier.

- [ ] **Step 4: Create `VaultActivity`**

```java
package dev.jasper.app;

import dev.jasper.app.vault.VaultService;
import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.JPopupMenu;
import javax.swing.Timer;

/** Counts real user input inside registered Jasper roots; repaints, output and timers never count. */
final class VaultActivity implements AutoCloseable {
    private final Supplier<VaultService> service;
    private final Consumer<Boolean> lockedNow;
    private final Set<Component> roots = new LinkedHashSet<>();
    private final AWTEventListener listener = this::event;
    private final Timer timer = new Timer(1000, e -> tick());
    private boolean installed, closed;

    VaultActivity(Supplier<VaultService> service, Consumer<Boolean> lockedNow, boolean start) {
        this.service = service; this.lockedNow = lockedNow;
        if (start) start();
    }

    void start() {
        if (installed || closed) return;
        installed = true;
        Toolkit.getDefaultToolkit().addAWTEventListener(listener, AWTEvent.KEY_EVENT_MASK | AWTEvent.MOUSE_EVENT_MASK
            | AWTEvent.MOUSE_MOTION_EVENT_MASK | AWTEvent.MOUSE_WHEEL_EVENT_MASK);
        timer.start();
    }

    AutoCloseable register(Component root) { roots.add(root); return () -> roots.remove(root); }

    int rootCount() { return roots.size(); }

    void event(AWTEvent event) {
        if (closed || !(event.getSource() instanceof Component source)) return;
        int id = event.getID();
        boolean input = id == KeyEvent.KEY_PRESSED || id == MouseEvent.MOUSE_PRESSED || id == MouseEvent.MOUSE_MOVED
            || id == MouseEvent.MOUSE_DRAGGED || id == MouseEvent.MOUSE_WHEEL;
        if (!input) return;
        for (Component cursor = source; cursor != null;
             cursor = cursor instanceof JPopupMenu popup ? popup.getInvoker() : cursor.getParent()) {
            if (roots.contains(cursor)) {
                VaultService current = service.get();
                if (current != null) current.userActivity();
                return;
            }
        }
    }

    void tick() {
        VaultService current = service.get();
        if (closed || current == null) return;
        boolean locked;
        try { locked = current.checkInactivity(); }
        catch (IOException impossible) { throw new IllegalStateException("In-memory inactivity check failed", impossible); }
        lockedNow.accept(locked);
    }

    @Override public void close() {
        if (closed) return;
        closed = true; timer.stop();
        if (installed) Toolkit.getDefaultToolkit().removeAWTEventListener(listener);
        roots.clear(); installed = false;
    }
}
```

- [ ] **Step 5: Create `VaultController`**

```java
package dev.jasper.app;

import dev.jasper.app.vault.*;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.*;

/**
 * Application-owned bridge between the vault service, the manager panel, dialogs and terminal windows.
 * All Swing work stays on the EDT; KDF, disk and keychain work runs on the single worker.
 */
final class VaultController implements AutoCloseable {
    static final class Dialog {
        final String title; final JComponent content; final JButton primary; final JComponent initial; final Runnable cancel;
        Runnable dispose = () -> {}, cleanup;
        Dialog(String title, JComponent content, JButton primary, JComponent initial, Runnable cancel) {
            this.title = title; this.content = content; this.primary = primary; this.initial = initial; this.cancel = cancel;
            this.cleanup = () -> clearComponent(content);
        }
    }
    private static final class Secret implements AutoCloseable {
        final char[] value;
        Secret(char[] value) { this.value = value; }
        @Override public void close() { Arrays.fill(value, (char) 0); }
        @Override public String toString() { return "Secret[redacted]"; }
    }
    private static final class KeyInput implements AutoCloseable {
        final byte[] bytes; final char[] phrase;
        KeyInput(byte[] bytes, char[] phrase) { this.bytes = bytes; this.phrase = phrase; }
        @Override public void close() { Arrays.fill(bytes, (byte) 0); Arrays.fill(phrase, (char) 0); }
    }
    private record Inspected(KeyInput input, VaultService.KeyPreview preview) implements AutoCloseable {
        @Override public void close() { input.close(); }
    }

    /** Core messages safe to show verbatim; anything else (native payloads, parser text) uses the caller's fallback. */
    private static final Set<String> CORE_MESSAGES = Set.of(
        "Password incorrect or vault authentication failed", "Unsupported vault format", "Unsupported vault KDF",
        "Invalid vault size", "Invalid ciphertext length", "Invalid master password characters", "Invalid master password length",
        "Vault changed externally; lock and unlock again before saving", "Vault is being saved by another instance",
        "Vault is being saved by another process", "Vault too large", "Vault is not a regular file", "Invalid vault directory",
        "Private vault permissions unavailable", "Cannot parse private key; check format and passphrase",
        "Cannot parse one supported private key; check format and passphrase", "Import exactly one private key",
        "Invalid private key size", "Private key required", "Supported keys: Ed25519, RSA 2048+, ECDSA P-256/P-384/P-521",
        "Invalid credential metadata", "Too many credentials", "Vault locked", "Vault already exists", "Vault already unlocked",
        "Login no longer exists", "Key no longer exists", "Key is used by a login", "OS credential store unavailable",
        "Remember duration must be between 1 and 365 days", "Remembered access could not be saved",
        "Vault unlocked, but remembered access could not be saved", "Remembered access revoked locally; OS deletion failed",
        "Remembered access is blocked now, but disk and OS deletion failed; restart revocation is not guaranteed",
        "OS remembered access deleted; local revocation marker could not be saved");

    static String sanitized(Exception failure, String fallback) {
        return failure instanceof IOException && failure.getMessage() != null && CORE_MESSAGES.contains(failure.getMessage())
            ? failure.getMessage() : fallback;
    }

    final VaultManagerPanel panel = new VaultManagerPanel();
    final VaultActivity activity;
    VaultUnlockForm unlockForm;
    Consumer<Dialog> present = dialog -> {};
    Runnable showManager = () -> {}, hideManager = () -> {};
    Supplier<Path> chooseFile = () -> null;

    private volatile VaultService service;
    private final Callable<VaultService> factory;
    private final Executor worker;
    private final Clipboard clipboard;
    private final Set<Runnable> listeners = new LinkedHashSet<>();
    private final Set<Dialog> dialogs = new LinkedHashSet<>();
    private final AutoCloseable panelActivity;
    private Dialog unlockDialog;
    private volatile long generation;
    private volatile boolean closed;
    private boolean busy, loadFailed;
    private long seenRevision = -1;

    VaultController(Callable<VaultService> factory) {
        this(null, factory, Executors.newSingleThreadExecutor(Thread.ofPlatform().daemon().name("jasper-vault-worker").factory()),
            Toolkit.getDefaultToolkit().getSystemClipboard());
    }
    VaultController(VaultService service, Executor worker, Clipboard clipboard) { this(service, null, worker, clipboard); }
    VaultController(Callable<VaultService> factory, Executor worker, Clipboard clipboard) { this(null, factory, worker, clipboard); }

    private VaultController(VaultService service, Callable<VaultService> factory, Executor worker, Clipboard clipboard) {
        this.service = service; this.factory = factory; this.worker = worker; this.clipboard = clipboard;
        activity = new VaultActivity(() -> this.service, this::activityChanged, false);
        panelActivity = activity.register(panel);
        panel.onAdd = () -> navigate(this::addLogin);
        panel.onImport = () -> navigate(() -> importKey(null));
        panel.onSettings = () -> navigate(this::settings);
        panel.onLock = () -> lock(true);
        panel.onSave = () -> save(() -> {});
        panel.onRevert = () -> panel.select(panel.selected());
        panel.onDelete = this::deleteSelected;
        panel.navigate = this::navigate;
        panel.login.reveal.addActionListener(e -> password(false));
        panel.login.copy.addActionListener(e -> password(true));
        panel.keyForm.copy.addActionListener(e -> copyPublicKey());
        refresh();
    }

    // ---- consumer API (worker-thread callers own and close the material) ----
    CredentialMaterial resolveLogin(UUID id) throws IOException {
        VaultService current = service;
        if (current == null) throw new IOException("Vault unavailable or locked");
        return current.resolveLogin(id);
    }
    CredentialMaterial resolveKey(UUID id, String username) throws IOException {
        VaultService current = service;
        if (current == null) throw new IOException("Vault unavailable or locked");
        return current.resolveKey(id, username);
    }
    void startActivity() { activity.start(); }
    VaultSnapshot snapshot() {
        return service == null
            ? new VaultSnapshot(false, true, List.of(), List.of(), VaultSettings.DEFAULT, null, 0, loadFailed ? "Vault metadata could not be read." : "")
            : service.snapshot();
    }
    AutoCloseable onChange(Runnable listener) { listeners.add(listener); return () -> listeners.remove(listener); }

    // ---- entry points ----
    void openManager() {
        if (closed) return;
        if (!busy && !panel.dirty() && unlockDialog == null) refresh();
        showManager.run();
        if (service == null && !busy) retryService();
    }

    /** Padlock and Tools menu: unlock (or create) through a dialog when locked; explicit lock when unlocked. */
    void toggleLock() {
        if (closed) return;
        if (service == null) { retryService(); return; }
        if (!snapshot().locked()) { lock(true); return; }
        promptUnlock();
    }

    void requestClose() { navigate(() -> { clearReveals(); hideManager.run(); }); }
    void cancelDialog(Dialog dialog) { if (!busy || dialog == unlockDialog) dialog.cancel.run(); }

    // ---- state ----
    private void refresh() {
        if (closed) return;
        VaultSnapshot s = snapshot(); seenRevision = s.revision();
        panel.showSnapshot(s);
        if (service == null) unavailable();
        else if (s.locked()) installUnlock();
        else unlockForm = null;
        panel.refreshButtons(busy);
        for (Runnable listener : List.copyOf(listeners)) listener.run();
    }

    private void unavailable() {
        unlockForm = null;
        JPanel card = new JPanel(new BorderLayout(0, 16)); VaultUi.pad(card, 24);
        card.add(new JLabel(loadFailed ? "Vault unavailable" : "Open credential vault"), BorderLayout.NORTH);
        card.add(new JLabel(loadFailed ? "Could not read vault metadata. Check file access and retry; existing data has not been changed."
            : "Opening this manager does not unlock the vault."));
        JButton retry = new JButton(loadFailed ? "Retry" : "Open vault");
        retry.addActionListener(e -> retryService());
        card.add(VaultUi.row(retry), BorderLayout.SOUTH);
        panel.lockedContent(card);
    }

    private void retryService() {
        if (service != null || factory == null || busy || closed) return;
        submit(factory, null, loaded -> { service = loaded; loadFailed = false; refresh(); },
            failure -> { loadFailed = true; refresh(); });
    }

    private VaultUnlockForm newUnlockForm() {
        VaultSnapshot s = snapshot();
        VaultUnlockForm form = new VaultUnlockForm(!s.exists(), s.deviceWarning() == null || s.deviceWarning().isBlank());
        form.state(s);
        boolean remembered = s.rememberedUntil() != null && s.rememberedUntil().isAfter(Instant.now());
        form.passwordVisible(form.create || !remembered);
        form.primary.addActionListener(e -> unlock(form));
        return form;
    }

    private void installUnlock() {
        unlockForm = newUnlockForm();
        unlockForm.cancel.addActionListener(e -> { lock(false); hideManager.run(); });
        panel.lockedContent(unlockForm);
    }

    private void promptUnlock() {
        if (unlockDialog != null) { present.accept(unlockDialog); return; }
        VaultUnlockForm form = newUnlockForm();
        form.cancel.addActionListener(e -> dismissUnlock());
        unlockDialog = new Dialog(form.create ? "Create vault" : "Unlock vault", form, form.primary, form.password, this::dismissUnlock);
        show(unlockDialog);
    }

    private void unlock(VaultUnlockForm form) {
        if (busy || closed) return;
        if (!form.create && !form.password.isVisible()) {
            submit(() -> service.unlockRemembered(), null,
                ok -> { if (ok) { dismissUnlock(); refresh(); } else form.showPassword("Device access expired or is missing. Enter the master password."); },
                failure -> form.showPassword("Device access could not be used. Enter the master password."));
            return;
        }
        char[] password = form.password.getPassword(), confirmation = form.confirmation.getPassword();
        if (password.length == 0 || (form.create && !Arrays.equals(password, confirmation))) {
            Arrays.fill(password, (char) 0); Arrays.fill(confirmation, (char) 0);
            form.message.setText("Enter a master password and matching confirmation."); return;
        }
        Arrays.fill(confirmation, (char) 0);
        Duration remember = form.remember.isSelected() ? Duration.ofDays(snapshot().settings().rememberDays()) : null;
        submit(() -> { if (form.create) service.create(password, remember); else service.unlock(password, remember); return true; },
            () -> Arrays.fill(password, (char) 0),
            ok -> { form.clear(); dismissUnlock(); refresh(); },
            failure -> {
                form.clear();
                if (!snapshot().locked()) { dismissUnlock(); refresh(); notice("Vault unlocked, but device access could not be remembered."); }
                else form.showPassword(sanitized(failure, "Vault could not be opened. Existing data is unchanged."));
            });
    }

    private void dismissUnlock() { if (unlockDialog != null) { Dialog d = unlockDialog; unlockDialog = null; dismiss(d); } }
    private void show(Dialog d) { dialogs.add(d); present.accept(d); }
    private void dismiss(Dialog d) { if (d == null) return; dialogs.remove(d); d.dispose.run(); d.cleanup.run(); }

    void lock(boolean explicit) {
        if (closed) return;
        boolean lost = panel.dirty(); generation++; busy = false;
        try { if (service != null) service.lock(VaultService.LockReason.AUTO); }
        catch (IOException impossible) { throw new IllegalStateException("In-memory vault lock failed", impossible); }
        List<Dialog> old = List.copyOf(dialogs); dialogs.clear(); unlockDialog = null;
        for (Dialog d : old) { d.dispose.run(); d.cleanup.run(); }
        if (unlockForm != null) unlockForm.clear();
        refresh();
        if (lost) notice("Vault locked. Unsaved edits were discarded.");
        if (explicit && service != null) {
            VaultService revoking = service;
            submit(() -> { revoking.lock(VaultService.LockReason.EXPLICIT); return true; }, null,
                ok -> refresh(), failure -> { refresh(); notice(deviceError()); }, true);
        }
    }

    private void notice(String text) { panel.message.setText(text); if (unlockForm != null) unlockForm.message.setText(text); }
    private String deviceError() {
        String text = snapshot().deviceWarning();
        return text == null || text.isBlank()
            ? (snapshot().locked() ? "Vault is locked. " : "") + "Device access could not be revoked; restart revocation is not guaranteed." : text;
    }

    private void activityChanged(boolean lockedNow) {
        if (closed || service == null) return;
        VaultSnapshot current = snapshot();
        if (lockedNow || (current.locked() && unlockForm == null)) lock(false);
        else if (!busy && current.revision() != seenRevision && !panel.dirty()) refresh();
    }

    private void clearReveals() { panel.login.hideSecret(); }
    private static void clearComponent(Component c) {
        if (c instanceof JPasswordField p) p.setText("");
        if (c instanceof Container p) for (Component child : p.getComponents()) clearComponent(child);
    }

    private void navigate(Runnable next) {
        if (busy || closed) return;
        if (!panel.dirty()) { clearReveals(); next.run(); return; }
        JPanel content = new JPanel(new BorderLayout(0, 14)); VaultUi.pad(content, 20);
        content.add(new JLabel("Save changes before leaving this credential?"));
        JButton save = new JButton("Save"), discard = new JButton("Discard"), cancel = new JButton("Cancel");
        content.add(VaultUi.row(cancel, discard, save), BorderLayout.SOUTH);
        Dialog[] holder = new Dialog[1];
        Runnable cancelled = () -> { panel.restoreSearch(); dismiss(holder[0]); };
        holder[0] = new Dialog("Unsaved credential changes", content, save, cancel, cancelled);
        save.addActionListener(e -> { dismiss(holder[0]); save(next); });
        discard.addActionListener(e -> { dismiss(holder[0]); panel.select(panel.selected()); next.run(); });
        cancel.addActionListener(e -> cancelled.run());
        show(holder[0]);
    }

    private void save(Runnable after) {
        UUID id = panel.selected(); if (id == null) return;
        var info = panel.findLogin(id);
        if (info != null) {
            var draft = panel.login.draft();
            if (!valid(draft, info.hasPassword())) { draft.close(); return; }
            submit(() -> service.saveLogin(id, draft.name(), draft.username(), draft.password(), draft.keyId()), draft,
                ignored -> { refresh(); panel.select(id); after.run(); },
                failure -> notice(sanitized(failure, "Could not save credential. The draft is retained; check the vault file and try again.")));
        } else {
            String name = panel.keyForm.draftName();
            if (name.isBlank()) { notice("A name is required."); return; }
            submit(() -> { service.renameKey(id, name); return id; }, null,
                ignored -> { refresh(); panel.select(id); after.run(); },
                failure -> notice(sanitized(failure, "Could not save key name. The draft is retained.")));
        }
    }

    private boolean valid(VaultLoginForm.Draft d, boolean previousPassword) {
        boolean hasPassword = d.password() == null ? previousPassword : d.password().length > 0;
        if (d.name().isBlank() || d.username().isBlank() || (d.keyId() == null && !hasPassword)) {
            notice("Name, username, and an SSH key or password are required."); return false;
        }
        return true;
    }

    private void addLogin() {
        if (snapshot().locked() || busy) return;
        VaultAddLoginDialog content = new VaultAddLoginDialog(snapshot().keys());
        VaultLoginForm form = content.form;
        Dialog[] h = new Dialog[1];
        Runnable cancelled = () -> { form.clear(); dismiss(h[0]); };
        h[0] = new Dialog("Add credential", content, content.add, form.name, cancelled);
        content.cancel.addActionListener(e -> cancelled.run());
        content.imported.addActionListener(e -> importKey(id -> {
            form.keys(snapshot().keys(), id);
            content.error.setText("Key imported into vault; cancelling this login keeps the key.");
        }));
        content.add.addActionListener(e -> {
            var d = form.draft();
            if (!valid(d, false)) { d.close(); content.error.setText("Name, username, and a key or password are required."); return; }
            submit(() -> service.saveLogin(null, d.name(), d.username(), d.password(), d.keyId()), d,
                id -> { form.clear(); dismiss(h[0]); refresh(); panel.select(id); },
                failure -> content.error.setText(sanitized(failure, "Could not save login. Your fields are retained.")));
        });
        show(h[0]);
    }

    private void deleteSelected() {
        if (busy || snapshot().locked()) return;
        UUID id = panel.selected(); if (id == null) return;
        var login = panel.findLogin(id); var key = panel.findKey(id);
        if (key != null && key.loginUses() > 0) {
            notice("This key is used by " + key.loginUses() + " login" + (key.loginUses() == 1 ? "" : "s") + ". Change those logins first.");
            return;
        }
        String name = login != null ? login.name() : key.name();
        JPanel content = new JPanel(new BorderLayout(0, 14)); VaultUi.pad(content, 20);
        content.add(new JLabel("Delete credential \"" + name + "\"? This cannot be undone."));
        JButton delete = new JButton("Delete"), cancel = new JButton("Cancel");
        content.add(VaultUi.row(cancel, delete), BorderLayout.SOUTH);
        Dialog[] h = new Dialog[1];
        h[0] = new Dialog("Delete credential", content, delete, cancel, () -> dismiss(h[0]));
        cancel.addActionListener(e -> dismiss(h[0]));
        delete.addActionListener(e -> {
            dismiss(h[0]);
            submit(() -> { if (login != null) service.deleteLogin(id); else service.deleteKey(id); return id; }, null,
                ignored -> { panel.select(null); refresh(); },
                failure -> notice(sanitized(failure, "Could not delete credential. Existing data is unchanged.")));
        });
        show(h[0]);
    }

    private void password(boolean copy) {
        if (busy || snapshot().locked()) return;
        UUID id = panel.selected(); var info = panel.findLogin(id); if (info == null) return;
        if (!copy && panel.login.revealed()) { panel.login.hideSecret(); return; }
        if (panel.login.hasTypedPassword()) {
            char[] typed = panel.login.password.getPassword();
            try { usePassword(typed, copy); } finally { Arrays.fill(typed, (char) 0); }
            return;
        }
        if (!info.hasPassword()) return;
        submit(() -> { try (var material = service.resolveLogin(id)) { return new Secret(material.password().clone()); } }, null,
            secret -> { try { if (id.equals(panel.selected())) usePassword(secret.value, copy); } finally { secret.close(); } },
            failure -> notice("Could not read the selected password."));
    }

    private void usePassword(char[] chars, boolean copy) {
        if (copy) {
            try { clipboard.setContents(new StringSelection(new String(chars)), null); notice("Password copied."); }
            catch (IllegalStateException unavailable) { notice("Clipboard is busy. Try again."); }
        } else panel.login.reveal(chars);
    }

    private void copyPublicKey() {
        var key = panel.findKey(panel.selected()); if (key == null) return;
        try { clipboard.setContents(new StringSelection(key.publicKey()), null); notice("Public key copied."); }
        catch (IllegalStateException unavailable) { notice("Clipboard is busy. Try again."); }
    }

    private void settings() {
        VaultSettingsForm form = new VaultSettingsForm(snapshot());
        Dialog[] h = new Dialog[1];
        Runnable cancel = () -> dismiss(h[0]);
        h[0] = new Dialog("Vault settings", form, form.save, form.auto, cancel);
        form.cancel.addActionListener(e -> cancel.run());
        form.save.addActionListener(e -> {
            try {
                form.auto.commitEdit(); form.days.commitEdit();
                VaultSettings settings = form.settings();
                submit(() -> { service.saveSettings(settings); return true; }, null,
                    ok -> { dismiss(h[0]); refresh(); },
                    failure -> form.message.setText(sanitized(failure, "Could not save settings. Previous settings remain active.")));
            } catch (java.text.ParseException | IllegalArgumentException invalid) {
                form.message.setText("Use 0–1440 minutes and 1–365 days.");
            }
        });
        form.forget.addActionListener(e -> {
            VaultService revoking = service;
            submit(() -> { revoking.forgetDeviceAccess(); return true; }, null,
                ok -> { dismiss(h[0]); refresh(); settings(); },
                failure -> form.message.setText(deviceError()), true);
        });
        show(h[0]);
    }

    private void importKey(Consumer<UUID> selected) {
        VaultImportForm form = new VaultImportForm();
        KeyInput[] inspected = new KeyInput[1]; Dialog[] h = new Dialog[1];
        Runnable release = () -> { if (inspected[0] != null) { inspected[0].close(); inspected[0] = null; } form.save.setEnabled(false); };
        Runnable cancel = () -> { release.run(); form.clear(); dismiss(h[0]); };
        h[0] = new Dialog("Import SSH key", form, form.inspect, form.name, cancel);
        h[0].cleanup = () -> { release.run(); form.clear(); };
        VaultUi.changes(form.passphrase, release);
        form.browse.addActionListener(e -> {
            Path path = chooseFile.get();
            if (path == null) return;
            release.run(); form.source = path; form.file.setText(path.getFileName().toString());
            if (form.name.getText().isBlank()) form.name.setText(path.getFileName().toString());
        });
        form.cancel.addActionListener(e -> cancel.run());
        form.inspect.addActionListener(e -> {
            if (form.source == null) { form.message.setText("Choose a private key file."); return; }
            Path source = form.source; char[] phrase = form.passphrase.getPassword(); release.run();
            submit(() -> {
                byte[] bytes;
                try (var in = Files.newInputStream(source)) { bytes = in.readNBytes(1024 * 1024 + 1); }
                if (bytes.length > 1024 * 1024) { Arrays.fill(bytes, (byte) 0); throw new IOException("Invalid private key size"); }
                KeyInput input = new KeyInput(bytes, phrase.clone());
                try { return new Inspected(input, service.inspectKey(input.bytes, input.phrase)); }
                catch (Exception failure) { input.close(); throw failure; }
            }, () -> Arrays.fill(phrase, (char) 0), result -> {
                if (!dialogs.contains(h[0])) { result.close(); return; }
                inspected[0] = result.input();
                form.fingerprint.setText(result.preview().algorithm() + " · " + result.preview().fingerprint());
                form.message.setText("Fingerprint inspected. Import stores an encrypted copy and leaves the source unchanged.");
                form.save.setEnabled(true);
            }, failure -> form.message.setText(sanitized(failure, "Could not parse the key. Check its format and enter a passphrase if encrypted.")));
        });
        form.save.addActionListener(e -> {
            if (inspected[0] == null) return;
            String name = form.name.getText().strip();
            if (name.isBlank()) { form.message.setText("A key name is required."); return; }
            KeyInput input = inspected[0]; inspected[0] = null; form.save.setEnabled(false);
            submit(() -> service.importKey(name, input.bytes, input.phrase), input, result -> {
                form.clear(); dismiss(h[0]); refresh(); panel.select(result.id());
                notice(result.duplicate() ? "Existing key selected; no duplicate was created." : "Key imported into encrypted vault.");
                if (selected != null) selected.accept(result.id());
            }, failure -> form.message.setText(sanitized(failure, "Could not import key. Inspect the source again before retrying.")));
        });
        show(h[0]);
    }

    // ---- worker plumbing ----
    private <T> void submit(Callable<T> operation, AutoCloseable input, Consumer<T> success, Consumer<Exception> failure) {
        submit(operation, input, success, failure, false);
    }

    private <T> void submit(Callable<T> operation, AutoCloseable input, Consumer<T> success, Consumer<Exception> failure, boolean mustComplete) {
        if (closed || busy) { closeValue(input); return; }
        busy = true; long ticket = generation;
        Map<Component, Boolean> enabledBefore = new IdentityHashMap<>();
        panel.refreshButtons(true);
        for (Dialog d : dialogs) { disableForWork(d.content, enabledBefore); if (d == unlockDialog) enableCancel(d.content); }
        if (unlockForm != null) { disableForWork(unlockForm, enabledBefore); unlockForm.cancel.setEnabled(true); }
        worker.execute(() -> {
            T result = null; Exception problem = null;
            try { if (mustComplete || (ticket == generation && !closed)) result = operation.call(); }
            catch (Exception failed) { problem = failed; }
            finally { closeValue(input); }
            T value = result; Exception error = problem;
            SwingUtilities.invokeLater(() -> {
                if (closed || ticket != generation) { closeValue(value); return; }
                busy = false; enabledBefore.forEach(Component::setEnabled); panel.refreshButtons(false);
                if (error != null) failure.accept(error); else success.accept(value);
            });
        });
    }

    private static void disableForWork(Component component, Map<Component, Boolean> previous) {
        previous.putIfAbsent(component, component.isEnabled()); component.setEnabled(false);
        if (component instanceof Container container) for (Component child : container.getComponents()) disableForWork(child, previous);
    }
    private static void enableCancel(Component c) {
        if (c instanceof JButton b && "Cancel".equals(b.getText())) b.setEnabled(true);
        if (c instanceof Container p) for (Component child : p.getComponents()) enableCancel(child);
    }
    private static void closeValue(Object value) {
        if (value instanceof AutoCloseable c) try { c.close(); } catch (Exception ignored) { /* best-effort wipe */ }
    }

    @Override public void close() {
        if (closed) return;
        lock(false); closed = true; generation++;
        activity.close(); closeValue(panelActivity); listeners.clear();
        if (service != null) service.close();
        if (worker instanceof ExecutorService owned) {
            owned.shutdown();
            Thread.ofPlatform().name("jasper-vault-shutdown").daemon(false).start(() -> {
                try { owned.awaitTermination(5, TimeUnit.SECONDS); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            });
        }
        showManager = () -> {}; hideManager = () -> {}; present = d -> {}; chooseFile = () -> null;
    }
}
```

- [ ] **Step 6: Run the tests**

```bash
./gradlew :jasper-app:test --tests 'dev.jasper.app.VaultActivityTest' --tests 'dev.jasper.app.VaultControllerTest'
```

Expected: BUILD SUCCESSFUL, 9 tests passed. Common failures and their causes: an `-Xlint` unchecked warning from `Consumer<Exception>` generics (add a local type, not `@SuppressWarnings`); the import test's key label assertion depends on `VaultLoginForm.keys` using `name + " · " + algorithm`, so the key name defaults to the file name `synthetic.key`.

- [ ] **Step 7: Hygiene, full check, commit**

```bash
./gradlew check
git add jasper-app/src/main/java/dev/jasper/app/VaultActivity.java jasper-app/src/main/java/dev/jasper/app/VaultController.java jasper-app/src/test/java/dev/jasper/app/VaultActivityTest.java jasper-app/src/test/java/dev/jasper/app/VaultControllerTest.java
git commit -m "feat: add the vault controller and inactivity tracking

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 7: Manager window and application wiring

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/VaultManagerWindow.java`
- Create: `jasper-app/src/main/java/dev/jasper/app/VaultWindowBinding.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/JasperApplication.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/TerminalWindow.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/Main.java:33-40`
- Create: `jasper-app/src/test/java/dev/jasper/app/VaultWindowBindingTest.java`

**Interfaces:**
- Consumes: `VaultController` (Task 6), `WindowContent.connectVault/disconnectVault/showVaultState` (Task 3), `VaultService`, `DeviceAccessStores.system()` (Task 1).
- Produces: `VaultWindowBinding implements AutoCloseable` with `static VaultWindowBinding bind(VaultController vault, WindowContent content, Component activityRoot)`; `VaultManagerWindow(VaultController)` with `void show()` and `close()`; `JasperApplication(ConfigService, ShellLauncher, CommandHistory, VaultController)` and `VaultController vault()`; `TerminalWindow.bindVault(VaultController)`.

- [ ] **Step 1: Write the failing binding test**

`jasper-app/src/test/java/dev/jasper/app/VaultWindowBindingTest.java`:

```java
package dev.jasper.app;

import dev.jasper.app.vault.VaultService;
import java.awt.datatransfer.Clipboard;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static dev.jasper.app.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

class VaultWindowBindingTest {
    @TempDir Path temp;
    @org.junit.jupiter.api.AfterEach void cleanup() throws Exception { closeOwners(); }

    @Test void bindingDrivesPadlockMenuTitleAndActivityRootUntilClosed() throws Exception {
        var jobs = new VaultControllerTest.Jobs();
        try (var service = new VaultService(temp.resolve("v.enc"), new VaultControllerTest.Store(), Clock.systemUTC())) {
            VaultController[] c = new VaultController[1]; WindowContent[] owner = new WindowContent[1];
            VaultWindowBinding[] binding = new VaultWindowBinding[1]; List<VaultController.Dialog> shown = new ArrayList<>();
            edt(() -> {
                c[0] = new VaultController(service, jobs, new Clipboard("test")); c[0].present = shown::add;
                owner[0] = content(launcher(new ArrayDeque<>()));
                var root = new JRootPane(); root.setContentPane(owner[0]);
                binding[0] = VaultWindowBinding.bind(c[0], owner[0], root);
                assertThat(c[0].activity.rootCount()).isEqualTo(2);
                assertThat(owner[0].status().vaultButton().isEnabled()).isTrue();
                assertThat(owner[0].action(ActionId.VAULT_LOCK).getValue(Action.NAME)).isEqualTo("Create Vault…");
                owner[0].status().vaultButton().doClick();
                assertThat(shown.getLast().title).isEqualTo("Create vault");
                var form = (VaultUnlockForm) shown.getLast().content;
                form.password.setText("synthetic master"); form.confirmation.setText("synthetic master"); form.primary.doClick();
            });
            jobs.drain();
            edt(() -> {
                assertThat(owner[0].action(ActionId.VAULT_LOCK).getValue(Action.NAME)).isEqualTo("Lock Vault");
                assertThat(owner[0].status().vaultButton().getIcon()).isSameAs(VaultIcons.icon("unlock"));
                owner[0].invoke(ActionId.VAULT_LOCK);
                assertThat(service.snapshot().locked()).isTrue();
                assertThat(owner[0].action(ActionId.VAULT_LOCK).getValue(Action.NAME)).isEqualTo("Unlock Vault…");
                binding[0].close();
                assertThat(c[0].activity.rootCount()).isEqualTo(1);
                assertThat(owner[0].status().vaultButton().isEnabled()).isFalse();
                assertThat(owner[0].action(ActionId.VAULT_MANAGER).isEnabled()).isFalse();
                c[0].close();
            });
            jobs.drain();
        }
    }
}
```

- [ ] **Step 2: Run to verify failure**

```bash
./gradlew :jasper-app:test --tests 'dev.jasper.app.VaultWindowBindingTest'
```

Expected: compilation failure, `VaultWindowBinding` missing.

- [ ] **Step 3: Create `VaultWindowBinding`**

```java
package dev.jasper.app;

import java.awt.Component;

/** Connects one terminal window's chrome and input to the shared vault controller. */
final class VaultWindowBinding implements AutoCloseable {
    private final WindowContent content;
    private final AutoCloseable subscription, registration;
    private boolean closed;

    private VaultWindowBinding(WindowContent content, AutoCloseable subscription, AutoCloseable registration) {
        this.content = content; this.subscription = subscription; this.registration = registration;
    }

    static VaultWindowBinding bind(VaultController vault, WindowContent content, Component activityRoot) {
        content.connectVault(vault::openManager, vault::toggleLock);
        AutoCloseable subscription = vault.onChange(() -> content.showVaultState(vault.snapshot()));
        AutoCloseable registration = vault.activity.register(activityRoot);
        content.showVaultState(vault.snapshot());
        return new VaultWindowBinding(content, subscription, registration);
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        try { subscription.close(); registration.close(); } catch (Exception ignored) { /* in-memory removals */ }
        content.disconnectVault();
    }
}
```

- [ ] **Step 4: Create `VaultManagerWindow`**

```java
package dev.jasper.app;

import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyChangeListener;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.swing.*;

/** The only native frame for the vault; all behavior lives in the controller and its panel. */
final class VaultManagerWindow implements AutoCloseable {
    private final VaultController controller;
    private final Set<JDialog> dialogs = new LinkedHashSet<>();
    private final PropertyChangeListener theme = e -> {
        if ("lookAndFeel".equals(e.getPropertyName())) SwingUtilities.invokeLater(this::refreshTheme);
    };
    private JFrame frame;
    private boolean closed;

    VaultManagerWindow(VaultController controller) {
        this.controller = controller;
        controller.showManager = this::show;
        controller.hideManager = () -> { if (frame != null) frame.setVisible(false); };
        controller.present = this::dialog;
        controller.chooseFile = this::chooseFile;
        UIManager.addPropertyChangeListener(theme);
    }

    private JFrame frame() {
        if (frame == null) {
            frame = new JFrame("Credential Vault");
            frame.setIconImages(ApplicationIcon.images(System.getProperty("os.name").startsWith("Mac")));
            frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
            frame.setContentPane(controller.panel);
            frame.addWindowListener(new WindowAdapter() {
                @Override public void windowClosing(WindowEvent e) { controller.requestClose(); }
            });
            frame.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "close");
            frame.getRootPane().getActionMap().put("close", VaultUi.action("Close", null, () -> { if (dialogs.isEmpty()) controller.requestClose(); }));
            frame.pack();
            frame.setMinimumSize(new Dimension(520, 400));
            frame.setLocationByPlatform(true);
        }
        return frame;
    }

    void show() { if (closed) return; frame().setVisible(true); frame.toFront(); }

    private Window dialogOwner() { return dialogs.isEmpty() ? frame() : List.copyOf(dialogs).getLast(); }

    private void dialog(VaultController.Dialog request) {
        if (closed) return;
        Window owner = dialogOwner();
        JDialog dialog = new JDialog(owner, request.title, Dialog.ModalityType.DOCUMENT_MODAL);
        dialogs.add(dialog);
        dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        dialog.setContentPane(request.content);
        dialog.getRootPane().setDefaultButton(request.primary);
        dialog.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancel");
        dialog.getRootPane().getActionMap().put("cancel", VaultUi.action("Cancel", null, () -> controller.cancelDialog(request)));
        dialog.addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { controller.cancelDialog(request); }
        });
        AutoCloseable activity = controller.activity.register(request.content);
        request.dispose = () -> {
            try { activity.close(); } catch (Exception ignored) { /* in-memory removal */ }
            dialogs.remove(dialog); dialog.dispose();
        };
        dialog.pack(); dialog.setLocationRelativeTo(owner);
        SwingUtilities.invokeLater(() -> {
            if (closed || !dialogs.contains(dialog)) return;
            SwingUtilities.invokeLater(request.initial::requestFocusInWindow);
            dialog.setVisible(true);
        });
    }

    private Path chooseFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Import private SSH key");
        AutoCloseable activity = controller.activity.register(chooser);
        try {
            return chooser.showOpenDialog(dialogOwner()) == JFileChooser.APPROVE_OPTION ? chooser.getSelectedFile().toPath() : null;
        } finally { try { activity.close(); } catch (Exception ignored) { /* in-memory removal */ } }
    }

    private void refreshTheme() {
        if (closed) return;
        if (frame != null) SwingUtilities.updateComponentTreeUI(frame);
        for (JDialog d : dialogs) SwingUtilities.updateComponentTreeUI(d);
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        UIManager.removePropertyChangeListener(theme);
        for (JDialog d : List.copyOf(dialogs)) d.dispose();
        dialogs.clear();
        if (frame != null) { frame.dispose(); frame = null; }
    }
}
```

- [ ] **Step 5: Wire `TerminalWindow`, `JasperApplication` and `Main`**

`TerminalWindow.java`: add a field `private VaultWindowBinding vaultBinding;` and the method

```java
    void bindVault(VaultController vault) {
        if (closed || vaultBinding != null) return;
        vaultBinding = VaultWindowBinding.bind(vault, content, frame.getRootPane());
    }
```

In `close()`, as the first statement after the `if (closed) return;` guard, add `if (vaultBinding != null) { vaultBinding.close(); vaultBinding = null; }`.

`JasperApplication.java`: add fields

```java
    private final VaultController vault;
    private VaultManagerWindow vaultWindow;
```

Change the three-argument constructor to delegate, and add the four-argument one:

```java
    JasperApplication(ConfigService service, ShellLauncher suppliedLauncher, CommandHistory history) {
        this(service, suppliedLauncher, history, null);
    }

    JasperApplication(ConfigService service, ShellLauncher suppliedLauncher, CommandHistory history, VaultController vault) {
        this.history = history;
        this.suppliedLauncher = suppliedLauncher;
        this.vault = vault;
        configuration = service == null ? null : new ConfigurationController(themes, service);
        if (vault != null && !GraphicsEnvironment.isHeadless()) { vaultWindow = new VaultManagerWindow(vault); vault.startActivity(); }
        if (supportsNativeQuit()) Desktop.getDesktop().setQuitHandler((event, response) -> {
            // Cancel the native immediate JVM exit; pane close owns bounded child cleanup.
            response.cancelQuit(); SwingUtilities.invokeLater(this::quit);
        });
    }

    VaultController vault() { return vault; }
```

In `newWindow`, after `windows.add(window);` add `if (vault != null) window.bindVault(vault);`. In `shutdown()`, after `if (configuration != null) configuration.close();` add:

```java
        if (vaultWindow != null) vaultWindow.close();
        if (vault != null) vault.close();
```

`Main.java`: add imports `dev.jasper.app.vault.DeviceAccessStores`, `dev.jasper.app.vault.VaultService`, `java.time.Clock`, and replace the body of the `invokeLater` lambda's `try` block:

```java
                    CommandHistory history = null;
                    JasperApplication application = null;
                    VaultController vault = null;
                    try {
                        history = new CommandHistory(dirs.commandHistory());
                        ApplicationIcon.installTaskbarIcon();
                        Path vaultFile = dirs.root().resolve("vault.enc");
                        vault = new VaultController(() -> new VaultService(vaultFile, DeviceAccessStores.system(), Clock.systemUTC()));
                        application = new JasperApplication(service, null, history, vault);
                        application.newWindow(Path.of(System.getProperty("user.home")));
                    }
                    catch (RuntimeException failure) {
                        LOG.log(System.Logger.Level.ERROR, "Application startup failed", failure);
                        if (application != null) application.quit();
                        else { if (vault != null) vault.close(); if (history != null) history.close(); }
                        service.close();
                        closeLogAfterStartupFailure(log, () -> {
                            exceptions.close();
                            removeShutdownHook(shutdown);
                        });
                    }
```

- [ ] **Step 6: Run the tests**

```bash
./gradlew :jasper-app:test --tests 'dev.jasper.app.VaultWindowBindingTest' --tests 'dev.jasper.app.WindowCommandPaletteTest' --tests 'dev.jasper.app.MainTest'
```

Expected: BUILD SUCCESSFUL. `WindowCommandPaletteTest` constructs `JasperApplication` with three arguments and must still compile.

- [ ] **Step 7: Hygiene, full check, commit**

```bash
./gradlew check
git add jasper-app/src/main/java/dev/jasper/app/VaultManagerWindow.java jasper-app/src/main/java/dev/jasper/app/VaultWindowBinding.java jasper-app/src/main/java/dev/jasper/app/JasperApplication.java jasper-app/src/main/java/dev/jasper/app/TerminalWindow.java jasper-app/src/main/java/dev/jasper/app/Main.java jasper-app/src/test/java/dev/jasper/app/VaultWindowBindingTest.java
git commit -m "feat: own the credential vault from the application

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 8: Previews, documentation and status

**Files:**
- Create: `jasper-app/src/test/java/dev/jasper/app/VaultPreview.java`
- Modify: `jasper-app/build.gradle.kts` (opt-in preview task list)
- Modify: `docs/design/credential-manager/README.md`
- Create: `docs/credential-vault.md`
- Modify: `docs/STATUS.md`, `docs/configuration.md`, `AGENTS.md`

**Interfaces:**
- Consumes: `VaultManagerPanel`, forms, `VaultFormsTest.example`, `VaultManagerPanelTest.two` (Tasks 4 and 5), `UiLookAndFeel`, `ThemeController`.

- [ ] **Step 1: Create the preview renderer**

`jasper-app/src/test/java/dev/jasper/app/VaultPreview.java`:

```java
package dev.jasper.app;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;
import javax.swing.*;

/** Headless renders of the actual manager components under each built-in look and feel; no JFrame. */
public final class VaultPreview {
    private VaultPreview() {}

    public static void main(String[] args) throws Exception {
        Path out = Path.of(args[0]);
        Files.createDirectories(out);
        var themes = new ThemeController();
        SwingUtilities.invokeAndWait(() -> {
            for (UiLookAndFeel laf : List.of(UiLookAndFeel.METAL, UiLookAndFeel.MOTIF, UiLookAndFeel.NIMBUS)) {
                themes.selectLaf(laf);
                String name = laf.name().toLowerCase(Locale.ROOT);
                var manager = new VaultManagerPanel();
                manager.showSnapshot(VaultManagerPanelTest.two()); manager.select(VaultManagerPanelTest.LOGIN);
                capture(manager, out.resolve("manager-" + name + ".png"), 900, 600);
                capture(manager, out.resolve("manager-narrow-" + name + ".png"), 560, 420);
                manager.select(VaultManagerPanelTest.KEY);
                capture(manager, out.resolve("manager-key-" + name + ".png"), 900, 600);
                var locked = new VaultManagerPanel();
                locked.showSnapshot(VaultFormsTest.example(true));
                var unlock = new VaultUnlockForm(false, true); unlock.state(VaultFormsTest.example(true));
                locked.lockedContent(unlock);
                capture(locked, out.resolve("manager-locked-" + name + ".png"), 900, 600);
                capture(new VaultAddLoginDialog(VaultFormsTest.example(false).keys()), out.resolve("add-login-" + name + ".png"), 480, 300);
                capture(new VaultImportForm(), out.resolve("import-" + name + ".png"), 620, 320);
                capture(new VaultSettingsForm(VaultFormsTest.example(false)), out.resolve("settings-" + name + ".png"), 540, 310);
            }
        });
        Files.writeString(out.resolve("render-manifest.txt"),
            "Actual Swing components rendered headlessly at 2x; dimensions are logical pixels; synthetic fixtures only.\n");
        System.out.println(out.toAbsolutePath());
    }

    static void capture(JComponent component, Path file, int width, int height) {
        component.setSize(width, height); VaultManagerPanelTest.layout(component);
        BufferedImage image = new BufferedImage(width * 2, height * 2, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setColor(UIManager.getColor("Panel.background")); g.fillRect(0, 0, image.getWidth(), image.getHeight());
            g.scale(2, 2); component.printAll(g);
        } finally { g.dispose(); }
        try { ImageIO.write(image, "png", file.toFile()); }
        catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
    }
}
```

In `jasper-app/build.gradle.kts`, extend the opt-in list:

```kotlin
for ((taskName, entryPoint) in listOf(
    "commandPalettePreview" to "CommandPalettePreview",
    "commandSearchMeasurement" to "CommandSearchMeasurement",
    "vaultPreview" to "VaultPreview",
)) {
```

- [ ] **Step 2: Render and inspect**

```bash
./gradlew :jasper-app:vaultPreview --args="$PWD/docs/design/credential-manager/renders"
ls docs/design/credential-manager/renders
```

Expected: 21 PNGs plus `render-manifest.txt`. Open `manager-motif.png`, `manager-narrow-motif.png`, `manager-locked-metal.png` and `add-login-nimbus.png` with the Read tool and confirm: sidebar left with counts, table above editor, Save/Revert at the lower right, status strip at the bottom, nothing clipped at the narrow size.

- [ ] **Step 3: Update the design README**

Append to `docs/design/credential-manager/README.md`:

```markdown
## Headless renders (2026-09-14)

Actual `VaultManagerPanel`, `VaultUnlockForm`, `VaultAddLoginDialog`, `VaultImportForm` and `VaultSettingsForm` components under Metal, Motif and Nimbus, rendered at 2× without a native frame. Sample records are synthetic fixtures. Reproduce with:

```sh
./gradlew :jasper-app:vaultPreview --args="$PWD/docs/design/credential-manager/renders"
```

| Look and feel | Manager | Key selected | Narrow | Locked | Add login | Import | Settings |
|---|---|---|---|---|---|---|---|
| Metal | [png](renders/manager-metal.png) | [png](renders/manager-key-metal.png) | [png](renders/manager-narrow-metal.png) | [png](renders/manager-locked-metal.png) | [png](renders/add-login-metal.png) | [png](renders/import-metal.png) | [png](renders/settings-metal.png) |
| Motif | [png](renders/manager-motif.png) | [png](renders/manager-key-motif.png) | [png](renders/manager-narrow-motif.png) | [png](renders/manager-locked-motif.png) | [png](renders/add-login-motif.png) | [png](renders/import-motif.png) | [png](renders/settings-motif.png) |
| Nimbus | [png](renders/manager-nimbus.png) | [png](renders/manager-key-nimbus.png) | [png](renders/manager-narrow-nimbus.png) | [png](renders/manager-locked-nimbus.png) | [png](renders/add-login-nimbus.png) | [png](renders/import-nimbus.png) | [png](renders/settings-nimbus.png) |

The mocks' "Used by" column and "Associated hosts" area are deferred to the SSH phase; the renders show the structure that ships now.
```

- [ ] **Step 4: Write the user guide**

`docs/credential-vault.md`:

```markdown
# Credential vault

The vault stores logins (username plus password and/or an SSH key) and imported SSH private keys in one encrypted file. SSH and SFTP will use it in later phases; nothing outside the manager reads it yet.

## Where it lives

- `vault.enc` in the Jasper config directory (next to `config.toml`): AES-256-GCM, key derived from the master password with Argon2id.
- `vault.enc.device`: a small nonsecret sidecar recording whether remembered device access exists and when it expires. It holds no key material.
- Remembered unlock material lives only in the OS credential store (macOS Keychain, Windows Credential Manager, or libsecret on Linux). Nothing secret is ever written to a plain settings file.

## Opening, creating and unlocking

- Status bar: the padlock at the right. Closed means locked; open means unlocked. Click it to unlock (or create the vault the first time) or to lock.
- Tools menu: **Credential Vault…** opens the manager; **Lock Vault / Unlock Vault…** is the same toggle as the padlock. Both are in the command palette (`Cmd/Ctrl+K`, type "vault"). Neither has a default shortcut; add one under `[keybindings]` with `vault_manager` or `vault_lock`.
- Jasper always starts locked. Unlocking asks for the master password. Tick **Remember on this device** to skip the password for 7 days (configurable 1–365). The deadline is fixed when you enter the password; using remembered access never extends it.

## Locking

- **Auto-lock** after 15 minutes without keyboard or mouse input in any Jasper window (configurable, 0 disables). Terminal output does not count as activity. Auto-lock keeps remembered access.
- **Explicit lock** (padlock, menu, or the manager's Lock vault button) also revokes remembered access; the next unlock needs the password.
- Locking clears the manager's editor and discards unsaved edits without asking.

## The manager

Sidebar: All credentials, Logins, SSH keys (with counts), then Settings and Import key. The table lists name, username and authentication method. Selecting a row loads the editor below; edits are drafts until **Save**, and **Revert** restores the saved values. Changing selection with unsaved edits asks Save / Discard / Cancel.

- **Add credential** opens the add-login form (login name, username, SSH key, password). **Import SSH key…** inside it imports a key and selects it while keeping the rest of the form.
- **Import key** copies a private key (OpenSSH or PEM; Ed25519, RSA 2048+, ECDSA P-256/384/521) into the encrypted vault after showing its fingerprint. The source file is left untouched and is no longer needed. Importing a key that already exists selects the existing entry.
- Passwords are never shown until you click reveal or copy.
- A key used by logins cannot be deleted; change those logins first.
- **Settings** (gear): auto-lock minutes, remembered duration, and **Forget this device**, which revokes remembered access without locking.

## Errors

A wrong password and a corrupted vault give the same message. A file that changed on disk while unlocked fails the next save and asks you to lock and unlock. If the OS credential store is unavailable, remembering is disabled and password unlock keeps working.
```

- [ ] **Step 5: Update STATUS, configuration and AGENTS**

Prepend to `docs/STATUS.md` (after the title line):

```markdown
**Credential vault (2026-09-14):** On `codex/credential-vault`, the reviewed vault core from `codex/rail-vault-implementation` is ported to `dev.jasper.app.vault` (Jasper file magic, keychain label, temp prefix) and a new vanilla-Swing manager is built on top: status-bar padlock, Tools menu, palette commands, sidebar/table/editor manager, add/import/settings/unlock dialogs, application-owned controller with inactivity auto-lock and remembered device access. Logins and SSH keys only; no SSH, rail or host associations. Fill in: final `./gradlew check` counts, review outcome. [Design](superpowers/specs/2026-09-14-jasper-credential-vault-design.md), [plan](superpowers/plans/2026-09-14-jasper-credential-vault.md), [guide](credential-vault.md), [renders](design/credential-manager/README.md). Native keychain enrollment and window checks remain user-run; no GUI, merge or push.
```

Replace "Fill in: …" with the real numbers after the final check in Task 9.

In `docs/configuration.md`, extend the action ID sentence (line 174) so the list ends with `..., open_settings, reload_config, vault_manager and vault_lock (the last two have no default shortcut)`.

In `AGENTS.md`, add a bullet under "Architecture rules":

```markdown
- Vault: package `dev.jasper.app.vault` is a port of the reviewed core from `codex/rail-vault-implementation` (see `docs/superpowers/specs/2026-09-13-jasper-vault-technical-design.md`); keep its file format and limits unless a spec changes them. UI work runs through `VaultController` on the EDT with one worker thread; secrets are `char[]`/`byte[]` wiped on close, error text shown to users comes only from `VaultController.sanitized`. Tests use temp dirs, `VaultControllerTest.Store` and injected clocks, never the real keychain.
```

- [ ] **Step 6: Check and commit**

```bash
./gradlew check
git add jasper-app/src/test/java/dev/jasper/app/VaultPreview.java jasper-app/build.gradle.kts docs
git commit -m "docs: add vault previews, user guide and status

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 9: Whole-branch review and verification ledger

**Files:**
- Modify: `docs/STATUS.md` (fill in counts), `docs/superpowers/plans/2026-09-14-jasper-credential-vault.md` (status banner)

- [ ] **Step 1: Fresh full check**

```bash
./gradlew check --rerun-tasks
python3 - <<'PY'
import pathlib
for name in [str(p) for p in pathlib.Path("jasper-app/src").rglob("*.java")]:
    text = pathlib.Path(name).read_text(encoding="utf-8")
    bad = sum(1 for c in text if 0xD800 <= ord(c) <= 0xDFFF or 0xE000 <= ord(c) <= 0xF8FF or (ord(c) < 0x20 and c not in "\n\t\r") or ord(c) == 0x7f)
    if bad: print(name, "bad chars:", bad)
PY
git diff --check main...HEAD
```

Expected: BUILD SUCCESSFUL; count tests from `jasper-app/build/test-results/test/*.xml` and `jasper-terminal/build/test-results/test/*.xml`; zero failures/errors; the one known `FontSetTest` skip.

- [ ] **Step 2: Whole-branch review**

Dispatch a reviewer over `git diff main...HEAD` against the spec. Required checks: no public method in `jasper-terminal` changed; no `String` holds a password outside the documented `new String(chars)` clipboard/reveal boundary; every `submit` failure path uses `sanitized`; `lock(false)` clears drafts and dialogs; `close()` order (lock, activity, service, worker); Tools menu index; `KeyBindings.defaults` tolerates `none`; no `sshd-core`; docs match behavior. Fix findings with tests, commit each fix.

- [ ] **Step 3: Record**

Fill in the STATUS entry's counts and review outcome; add a status banner at the top of this plan listing any deviation from the plan text. Commit:

```bash
git add docs
git commit -m "docs: record credential vault verification

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

Do not merge or push; hand the branch to the user with the native acceptance list from `docs/credential-vault.md` (macOS Keychain enrollment, remembered unlock, Forget this device, window focus).
