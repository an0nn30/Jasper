# Linux residency 2: modern and legacy trays Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a recoverable Linux resident icon through Java D-Bus StatusNotifierItem/DBusMenu, falling back to X11 while retaining residency with no icon.

**Architecture:** An application-owned `LinuxResidentTray` coordinates two concrete adapters behind `ResidentTray.install`. A small pure policy selects the preferred capability; a generation guards all asynchronous publications and actions. D-Bus owns its connection and exports; an owned XCB connection provides the legacy icon and an on-demand Swing popup owns its menu.

**Tech Stack:** JBR 25, dbus-java core plus one Unix transport, existing JNA 5.14.0/XCB and public Swing, JUnit 5/AssertJ, isolated `dbus-daemon`, Gradle wrapper.

**Spec:** [Approved Linux residency design](../specs/2026-10-27-linux-residency-design.md) and [approved XCB amendment](../specs/2026-10-28-linux-x11-tray-amendment-design.md).

**Task 9 status (2026-10-28): IMPLEMENTED; REVIEW PENDING.** Production Linux routing composes the reviewed controller, modern adapter, owned XEmbed adapter and popup after the existing settled hook. One daemon scheduler physically cancels retry/probe futures; successful modern publication suspends the sole five-second probe and confirmed withdrawal resumes a fresh snapshot. Fedora passes 57 required-native tests, 267 focused tests (one existing macOS-only skip), both composed real-panel probes and 20 transport watchdog cases; local 263 tests have 67 Linux-only skips. Ruling 32 separates the 100 ms idle wait from the existing bounded native-operation deadline after ordinary-load XFCE evidence and a deterministic RED regression. Synchronous factory observations stay cold until start. The probe now isolates its modern fixture on a second private bus because XFCE's own modern host correctly wins preference. Wrapper reproduction forces private environment before Xvfb outer/private bus inner. Task 10 retains full check, packages, CI and final acceptance.

**Task 8 status (2026-10-28): IMPLEMENTED; REVIEW PENDING.** User-approved managed-popup correction and controller Rulings 26/28–30 implement verified single-device placement, real press timestamps, bounded own-peer hints and one WM-mediated activation request. All four Fedora panel/scale rows and 109 focused native/portable tests pass. No live/focus-owned resources remain; at most one disposed raw AWT inventory entry survives bounded GC. Failed metadata paths were removed; verbose poll-deadline sensitivity and physical mixed-monitor acceptance remain documented limitations. Production routing remains Task 9.

**Revision status (2026-10-28):** Task 8 and fix `0fa60e60d` passed independent review and scoped re-review; Tasks 9–10 remain pending.  Task 7 and fix `83ba62837` passed independent review and scoped re-review; Tasks 8–10 remain pending.  Task 6 implementation `57bc1361f` passed independent review; Ruling 22 records worker-scoped binding loading during `Cancellation` construction before connect. Task 7 is implemented with Fedora native/panel gates passed; independent review pending. Ruling 24 uses unchecked void requests plus tracked sequences and barrier/error draining; Ruling 25 sets a 2x2 minimum hint because XFCE treats 1x1 as hidden, while assigned 1..512 slots remain valid. Native ownership stays in LinuxTrayXcb; the optional Ruling 23 helper split was not used. Tasks 8–10 remain pending. Task 6 implementation passes 21 Fedora native tests and 20 watchdog cases; per-task review passed. Three discovered source-inventory rows and same-size snapshot timestamp checks are scoped additions. Task 6 native ABI evidence corrects `O_NOFOLLOW` to architecture-specific x86_64 `0x20000` / aarch64 `0x8000`; the original common constant failed the actual aarch64 symlink regression. User approved the [authority-I/O correction](../specs/2026-10-28-linux-x11-tray-amendment-design.md#authority-io-correction--approved-2026-10-28): authority acquisition is cooperative with one pending daemon worker/FD; canceled late results cannot connect/publish, modern publication and Quit remain responsive, and strict socket cancellation still requires native proof. User approved the XCB amendment and continuing the existing per-task implementer/reviewer workflow. Tasks 1–5 below are preserved records; Task 2 is FAILED and its AWT approach is superseded. Remaining map: **6** bounded XCB/auth/ABI gate → **7** native embedding/render/input and real-panel gate → **8** transient Swing popup and placement gate → **9** production composition/routing → **10** CI/packages/docs/final acceptance. This is one runnable tray deliverable. Tasks 6–10 replace former AWT Task 6 and final Task 7. Native gate failure requires a fix or amendment; it never grants permission to assume a pass. The remaining tasks below implement the approved amendment; no production fallback is wired by this planning revision.

**Historical status through completed Tasks 1–5 (superseded remaining-task references):** Tasks 1, 3, 4 and 5 have approved per-task reviews, including scoped fix reviews (`d77c7e7cb`, `2d291bb18`). Task 2 native characterization ran with authorization on 2026-10-28; the public-AWT gate failed. The user subsequently approved the [XCB/JNA amendment](../specs/2026-10-28-linux-x11-tray-amendment-design.md); revised Tasks 6–10 and the final whole-branch review remain pending. Task 1 passed the user-directed Fedora 44 aarch64 gate: 49 focused tests and both packaged-runtime address cases, notices/architecture and headless packaging. [Verification](2026-10-27-linux-residency-2-verification.md) records exact versions and evidence. Rulings 9–10 follow the user's split: x86_64 and Ubuntu 22.04 remain CI-pending; after Task 1 review, Tasks 4–5 may proceed with the pinned 5.2.0 bindings below. Task 2 retains the failed AWT gate evidence. Rulings 1–2 allow local preparation and replace the fixture sketch with short `/tmp` paths and bounded reader/process cleanup. Ruling 4 selects junixsocket after the released JDK transport's abstract-address rejection; Ruling 5 aligns application SLF4J API/nop to 2.0.17. Ruling 7 replaces the non-JAR aggregator with its exact common/native-common 2.10.1 jars. Ruling 8 excludes the confirmed compile-only annotation leak after packaging reproduced its module failure, superseding Ruling 6's temporary EPL investigation. Costs: native artifacts, explicit dependency tracking and a logging-version regression surface. The CI baseline and desktop matrix are not certified. Task 2 native characterization is recorded: the safe public-AWT gate failed on LXDE/JBR b635.75; Remaining implementation follows the approved legacy fallback amendment. Ruling 16 corrects the disposable probe's no-host lifetime with a bounded main-thread wait. Task 3's shared menu, ARGB conversion and capability/retry policy pass focused tests and architecture/inventory checks. Deviation: add Task 3's four new Java sources to the theme source inventory because its source discovery scans AWT imports, including headless tests. Task 4 implements the typed wire exports and bounded owner-scoped adapter; 19 Fedora private-bus tests pass, including independent exact `gdbus` introspection for both address forms. Controller Ruling 12 requires generic Tuple carriers and explicit Variant returns from `Properties.Get`; Ruling 13 narrowly repairs the pinned library's duplicated tuple-output introspection through public metadata APIs. Both accommodations are version-specific and must be revalidated on upgrade. Task 4 review fix 1 drains cleanup requests and releases worker ownership in one locked handoff, with a package-private no-op Runnable seam for deterministic tests; publication retains the discovery endpoint and verifies the daemon GetId before registration. Two required source-inventory rows were added. Earlier deviations: add the native test probe to the theme source inventory and correct Task 1 evidence wording (ARM64 passed; existing cold Linux compiler warnings retained). Task 5 passes 50 Fedora ARM64 private-bus/controller tests (no skips) and local controller/inventory/architecture checks. Ruling 14 adds separate connection-state observations and a Modern reconnect callback: the controller owns the sole backoff timer, the adapter owns bounded attempts, and connected-without-host stops reconnects. Ruling 15 adds bounded worker-only `withdrawAndAwait`; Task 6 must bind it before replacement publication. One test inventory row added. Injectable timers are logically invalidated; Revised Task 9 owns physical cancellation, daemon scheduling and EDT/probe composition after native proof of the replacement legacy design.

## Global Constraints

- Residency is a mode of the Jasper process, independent of whether the desktop can show a tray icon.
- Prefer a successfully registered modern item with a reported live host. A watcher service alone is insufficient evidence of a host.
- Switching withdraws the previous publication before publishing the replacement, so Jasper never deliberately publishes two icons.
- Start discovery once ordinary startup or background warm-up has settled, using the existing indicator hook.
- Keep D-Bus types inside the Linux platform adapter; the application sees only its existing callbacks/cleanup.
- Menu: Open Jasper, New Window, Settings…, separator, Quit Jasper. Existing callbacks and close guards own behavior.
- One Unix transport only; ship every dependency/native component with license notices. No new GTK/Qt dependency.
- JBR 25 on x86_64 and aarch64. Preserve Ubuntu 22.04 package baseline and existing Linux package identity `jasper-workstation`.
- No GUI, tray probe, benchmark or AOT training without explicit authorization. Linux headless bus tests may use only a private test bus. Keep fixture socket paths short (create fixture directories directly under `/tmp` on Linux), and fail startup within five seconds.
- Follow the repository's per-task implementer/reviewer workflow, final whole-branch review, one commit per task with `Co-Authored-By: Codex <noreply@openai.com>`. No automatic merge/push.
- Protocol declarations represent real external contracts used by Jasper and desktop hosts, not new backend abstraction interfaces. Use records/functions for controller seams and concrete adapters. Keep everything in `dev.jasper.app.platform` to preserve the package DAG.

## Review Focus

1. A watcher starts before its panel host, or its owner changes during registration — Tasks 4–5 owner/host and stale-generation tests.
2. A queued click arrives after an icon was withdrawn or Quit began — Task 5 EDT-generation test.
3. An abstract Unix session address or packaged transport is unsupported — Task 1 real-address and packaged runtime gate.
4. A legacy panel restarts, or GNOME enables its extension while the fallback is shown — Tasks 5/7/9 recovery and single-publication tests; Task 2 preserves the failed AWT evidence.
5. Cold background login races with a foreground launch or logout — Task 10 native session matrix plus existing bootstrap tests; a tray disappearance is never interpreted as logout.

## File map

All new production Java below is under `jasper-app/src/main/java/dev/jasper/app/platform/`; same-package tests live under `jasper-app/src/test/java/dev/jasper/app/platform/`.

| File | Responsibility |
|---|---|
| `LinuxTrayPolicy.java` | Pure target selection and capped retry delay |
| `LinuxTrayMenu.java` | Stable IDs, labels and action dispatch shared by both adapters |
| `LinuxTrayDbus.java` | Wire interfaces and value structs, with exact D-Bus signatures |
| `LinuxStatusNotifier.java` | Session connection, watcher discovery, exports and modern registration |
| `LinuxTrayXcb.java` | Lazy native ABI, bounded transport/auth, request/reply and image bindings |
| `LinuxLegacyTray.java` | Owned XEmbed publication, host epochs, native worker, bounded cleanup |
| `LinuxTrayPopup.java` | Transient EDT Swing menu and public-coordinate placement |
| `LinuxResidentTray.java` | Scheduling, generation, backend switching, bounded close |
| `ResidentTray.java` (existing) | Linux delegation; retain Windows balloon and macOS behavior |
| `LinuxTestBus.java`, `LinuxTrayProbe.java` (tests only) | Private bus fixture and opt-in native capability probe |
| `LinuxTrayTransportTest.java`, `LinuxTrayPolicyTest.java`, `LinuxTrayMenuTest.java`, `LinuxStatusNotifierTest.java`, `LinuxResidentTrayTest.java`, `LinuxTrayWireTest.java` | Tests by ownership |
| `jasper-app/build.gradle.kts`, `.github/workflows/linux-packages.yml` | Dependencies and required headless Linux transport/wire checks |
| `docs/superpowers/plans/2026-10-27-linux-residency-2-verification.md` | Evidence artifact created during execution, not a current guide |

No source file is created merely as scaffolding: each lands with its behavior and test in its task. The verification report records exact artifacts/versions, commands and user-run results; never prefill passing results.

### Task 1: Resolve and prove the D-Bus dependency/transport

**Files:** Modify `jasper-app/build.gradle.kts`, `jasper-app/src/main/resources/dev/jasper/app/about/licenses/notices.toml`; add needed license texts beside that manifest; create test files `LinuxTestBus.java`, `LinuxTrayTransportTest.java`; create the verification report above.

**Interfaces:** Test fixture `LinuxTestBus.start(Path directory, boolean abstractAddress)`, `String address()`, `close()`; a successful private-bus connection through `DBusConnectionBuilder.forAddress(String).withShared(false).build()`. Later code uses owned, unshared connections.

- [x] **Step 1: Add the test fixture and failing transport test.** This is Linux-only and uses no display or existing session bus. Imports in these complete classes are explicit.

```java
package dev.jasper.app.platform;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

final class LinuxTestBus implements AutoCloseable {
    private final Process process;
    private final String address;
    private LinuxTestBus(Process process, String address) {
        this.process = process;
        this.address = address;
    }
    static LinuxTestBus start(Path directory, boolean abstractAddress) throws Exception {
        String requested = abstractAddress ? "unix:abstract=jasper-test-" + UUID.randomUUID()
            : "unix:path=" + directory.resolve("bus-" + UUID.randomUUID());
        Process process = new ProcessBuilder("dbus-daemon", "--session", "--nofork", "--nopidfile",
            "--print-address=1", "--address=" + requested)
            .redirectError(ProcessBuilder.Redirect.INHERIT).start();
        try {
            var address = CompletableFuture.supplyAsync(() -> {
                try {
                    return new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)).readLine();
                } catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
            }).get(5, TimeUnit.SECONDS);
            if (address == null || !address.startsWith("unix:"))
                throw new IllegalStateException("Test bus did not report a Unix address");
            return new LinuxTestBus(process, address);
        } catch (Exception failure) {
            process.destroyForcibly();
            process.waitFor(5, TimeUnit.SECONDS);
            throw failure;
        }
    }
    String address() { return address; }
    @Override public void close() throws Exception {
        process.destroy();
        if (!process.waitFor(3, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            if (!process.waitFor(3, TimeUnit.SECONDS)) throw new IllegalStateException("Test bus did not stop");
        }
    }
}
```

```java
package dev.jasper.app.platform;

import java.nio.file.Path;
import java.time.Duration;
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

@EnabledOnOs(OS.LINUX)
class LinuxTrayTransportTest {
    @TempDir Path directory;
    @Test void connectsToFilesystemAndAbstractBuses() {
        assertTimeoutPreemptively(Duration.ofSeconds(20), () -> {
            for (boolean abstractAddress : new boolean[]{false, true}) {
                try (var bus = LinuxTestBus.start(directory, abstractAddress);
                     var connection = DBusConnectionBuilder.forAddress(bus.address()).withShared(false).build()) {
                    assertThat(connection.getUniqueName()).startsWith(":");
                    assertThat(connection.isConnected()).isTrue();
                }
            }
        });
    }
}
```

- [x] **Step 2: Run** `./gradlew :jasper-app:compileTestJava`; expect unresolved dbus imports. Do not run a transport test against `DBUS_SESSION_BUS_ADDRESS` from the user's desktop.
- [x] **Step 3: Resolve artifacts and select exactly one transport.** Published dbus-java 5.2.0 and junixsocket 2.10.1 are pinned; released JDK transport source explicitly rejects abstract addresses. Fedora aarch64 private-bus and packaged-runtime tests pass. Exact runtime declarations (Rulings 4–8):

```kotlin
implementation("com.github.hypfvieh:dbus-java-core:5.2.0")
runtimeOnly("com.github.hypfvieh:dbus-java-transport-junixsocket:5.2.0") {
    exclude(group = "com.kohlschutter.junixsocket", module = "junixsocket-core")
    exclude(group = "com.kohlschutter", module = "compiler-annotations")
}
runtimeOnly("com.kohlschutter.junixsocket:junixsocket-common:2.10.1")
runtimeOnly("com.kohlschutter.junixsocket:junixsocket-native-common:2.10.1")
runtimeOnly("org.slf4j:slf4j-nop:2.0.17")
```

The verification report records exact artifact/license/native inventory, maintenance/advisory evidence,
provider discovery, the two metadata failures and their corrections. Exactly one application NOP provider
and transport are present. The plugin-private 2.0.13 providers remain separate.

- [x] **Step 4: Run the user-directed aarch64 gate.** `dependencies`, the two-address private-bus test, notices/architecture, `installDist`, `packageApp` and `verifyPackage -Pjasper.aot=false` passed on Fedora 44/JBR 25 aarch64. The test also passed twice using only packaged JBR/production jars plus test artifacts. **Rulings 9–10 leave x86_64 and Ubuntu 22.04 baseline execution/install pending in CI**, with explicit release evidence required; no macOS skip is counted as Linux evidence. Windows headless compatibility and final full check remain pending.
- [x] **Step 5: Commit the dependency pin and evidence** with the required trailer. **Gate under the user-directed split:** Tasks 4–5 may begin after Task 1 review. Task 6 still waits for Task 2's AWT evidence. Remaining CI rows stay required acceptance.

**Frozen 5.2.0 bindings:** `DBusConnectionBuilder.forAddress(String).withShared(false).build()`,
`withDisconnectCallback(IDisconnectCallback)`, `transportConfig().withTimeout(int).back()`, connection
`getRemoteObject(..., Class<T>)`, `exportObject`/`unExportObject`, `close`, and `addSigHandler` returning
an `AutoCloseable` subscription. `Properties` has generic `Get`/`Set` and `Map<String,Variant<?>> GetAll`;
`Variant(value, String signature)` and the three planned property error classes are present.
Task 4 additionally verifies `transportConfig().withRegisterSelf(false)` followed by owned `register()`;
interruptible scoped deadlines bound authentication and calls without a global timeout. Ruling 12 uses
generic Tuple carriers and returns the explicit Variant from `Properties.Get` to preserve compound/empty
signatures. Ruling 13 corrects only the duplicate output suffixes of `GetLayout` and `AboutToShowGroup`
in 5.2.0's generated metadata using public `ExportedObject`/`getObjectTree().add` before registration;
an unexpected metadata shape refuses publication. Typed dispatch and actual replies remain unchanged.
**Timeout caveat:** transport timeout config controls selection/retry, not blocking connection/call duration;
`MethodCall.setDefaultTimeout` is global. Task 4 owns scoped deadlines, cancellation/close and unresponsive
watcher tests. Wire declarations and exact exported signatures must still pass its private-bus tests.

### Task 2: Prove the public AWT fallback before relying on it

**2026-10-28 execution: native public-AWT gate FAILED.** After the user installed the approved prerequisites, owned LXDE/XFCE panels ran on private Xvfb displays/buses. Normal 1x/2x rendering, late hosts, action/menu dispatch and live applet re-add worked, but LXDE panel loss throws an uncaught JBR exception before documented availability callbacks can prevent it; Jasper would turn it into an incident notification. Task 6 is stopped pending the required reviewed legacy fallback amendment. Ruling 16 keeps the test-only main alive for at most 185 seconds because the supplied Swing timer alone let the no-host probe exit after 1.39 seconds; the 180-second normal exit remains. Public callbacks/rendering variants are disposable diagnostics only. Full evidence and limitations are in [verification](2026-10-27-linux-residency-2-verification.md#task-2-native-result--2026-10-28-public-awt-gate-failed).

**Files:** Create test-only `LinuxTrayProbe.java`; modify verification report; no production fallback until the gate passes.

**Interfaces:** Explicit opt-in test-class `main(String[] args)`; no Gradle `check` dependency. Its normal exit is three minutes, with a bounded 185-second main-thread fallback (Ruling 16); it never launches Jasper or a shell.

- [x] **Step 1: Add this complete disposable native probe.**

```java
package dev.jasper.app.platform;

import java.awt.Color;
import java.awt.GraphicsEnvironment;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.image.BufferedImage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

public final class LinuxTrayProbe {
    public static void main(String[] args) throws Exception {
        if (!System.getProperty("os.name", "").equalsIgnoreCase("Linux") || GraphicsEnvironment.isHeadless())
            throw new IllegalStateException("Opt-in Linux graphical-session probe only");
        SwingUtilities.invokeAndWait(() -> {
            TrayIcon[] current = new TrayIcon[1];
            SystemTray[] owner = new SystemTray[1];
            Runnable remove = () -> {
                if (current[0] != null) {
                    owner[0].remove(current[0]);
                    current[0] = null;
                    System.out.println("removed");
                }
            };
            Timer poll = new Timer(2000, event -> {
                try {
                    if (!SystemTray.isSupported()) { remove.run(); System.out.println("no host"); return; }
                    if (current[0] != null) return;
                    BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
                    var g = image.createGraphics();
                    try { g.setColor(Color.ORANGE); g.fillOval(4, 4, 24, 24); }
                    finally { g.dispose(); }
                    PopupMenu menu = new PopupMenu();
                    MenuItem item = new MenuItem("Record click");
                    item.addActionListener(click -> System.out.println("menu action EDT=" + SwingUtilities.isEventDispatchThread()));
                    menu.add(item);
                    TrayIcon icon = new TrayIcon(image, "Jasper legacy tray probe", menu);
                    icon.setImageAutoSize(true);
                    icon.addActionListener(click -> System.out.println("activate"));
                    owner[0] = SystemTray.getSystemTray();
                    owner[0].add(icon);
                    current[0] = icon;
                    System.out.println("added slot=" + SystemTray.getSystemTray().getTrayIconSize());
                } catch (Exception failure) { System.out.println(failure); }
            });
            poll.setInitialDelay(0);
            poll.start();
            Timer end = new Timer(180_000, event -> { poll.stop(); remove.run(); System.exit(0); });
            end.setRepeats(false);
            end.start();
        });
        // A Swing timer alone cannot keep AWT alive while no tray host exists.
        new CountDownLatch(1).await(185, TimeUnit.SECONDS);
        System.exit(1); // Bounded fallback if the EDT cannot deliver the three-minute timer.
    }
}
```

- [x] **Step 2: Compile headlessly** with `./gradlew :jasper-app:compileTestJava`. Compilation passing proves only that the public API is usable.
- [x] **Step 3: Hand the probe to the user for an authorized session run** with the bundled JBR, `-Djava.awt.headless=false`, application/test classpaths, and main `dev.jasper.app.platform.LinuxTrayProbe`. Run LXDE on X11 and a second legacy tray configuration (MATE/XFCE legacy host). Start with no host, enable it, restart the panel, remove/re-add the tray applet, click menu/activation, inspect alpha and 1×/2× scale. Record whether the original icon returns after panel restart and whether Java notices host loss. The probe's `current` reference is intentionally retained to characterize automatic JBR recovery. A visible ghost or lost icon is a failure even if `getTrayIcons()` still lists it.
- [x] **Step 4: Record PASS/failure evidence**, including whether public support/property signals suffice or a periodic remove/re-add is necessary. If the public API supports recovery, proceed with the adapter below. If it does not, stop Task 6 and amend the design for an X11/JNA fallback; do not import `sun.awt` or use an endless icon-reinstall timer as an unreviewed workaround. Independent protocol work may proceed after Task 1 while this gate awaits the user.
- [x] **Step 5: Commit** `test: characterize Linux legacy tray recovery` with trailer. Task completion requires recorded native evidence; committing the fixture alone leaves the gate pending.

### Task 3: Shared menu, icon bytes and capability policy

**Files:** Create `LinuxTrayPolicy.java`, `LinuxTrayMenu.java`; tests `LinuxTrayPolicyTest.java`, `LinuxTrayMenuTest.java`. Pure/headless, no D-Bus connection.

**Interfaces:** `LinuxTrayPolicy.target(boolean modern, boolean legacy)`, `retrySeconds(int failures)`; `LinuxTrayMenu.rows()`, `invoke(int id, ResidentTray.Actions actions)`, `argb(BufferedImage)`. Values are copied into adapter-owned wire values; no mutable image crosses a worker boundary.

- [x] **Step 1: Add complete test methods** with JUnit/AssertJ imports:

```java
@Test void modernWinsAndNoHostIsAnOrdinaryState() {
    assertThat(LinuxTrayPolicy.target(true, true)).isEqualTo(LinuxTrayPolicy.Target.MODERN);
    assertThat(LinuxTrayPolicy.target(true, false)).isEqualTo(LinuxTrayPolicy.Target.MODERN);
    assertThat(LinuxTrayPolicy.target(false, true)).isEqualTo(LinuxTrayPolicy.Target.LEGACY);
    assertThat(LinuxTrayPolicy.target(false, false)).isEqualTo(LinuxTrayPolicy.Target.NONE);
    assertThat(java.util.stream.IntStream.range(0, 8).map(LinuxTrayPolicy::retrySeconds).toArray())
        .containsExactly(1, 2, 4, 8, 16, 30, 30, 30);
}

@Test void bothBackendsUseStableMenuActionsAndIgnoreUnknownIds() {
    var events = new java.util.ArrayList<String>();
    var actions = new ResidentTray.Actions(() -> events.add("open"), () -> events.add("new"),
        () -> events.add("settings"), () -> events.add("quit"));
    assertThat(LinuxTrayMenu.rows()).extracting(LinuxTrayMenu.Row::id).containsExactly(1, 2, 3, 4, 5);
    for (int id : new int[]{0, 1, 2, 3, 4, 5, 99}) LinuxTrayMenu.invoke(id, actions);
    assertThat(events).containsExactly("open", "new", "settings", "quit");
}

@Test void pixmapsUseNetworkOrderArgbIncludingTransparency() {
    var image = new java.awt.image.BufferedImage(2, 1, java.awt.image.BufferedImage.TYPE_INT_ARGB);
    image.setRGB(0, 0, 0x80123456);
    image.setRGB(1, 0, 0x00000000);
    assertThat(LinuxTrayMenu.argb(image)).containsExactly((byte) 0x80, (byte) 0x12, (byte) 0x34,
        (byte) 0x56, (byte) 0, (byte) 0, (byte) 0, (byte) 0);
}
```

- [x] **Step 2: Run** `./gradlew :jasper-app:test --tests '*LinuxTrayPolicyTest' --tests '*LinuxTrayMenuTest'`; expect missing classes.
- [x] **Step 3: Add these complete production classes.**

```java
package dev.jasper.app.platform;

final class LinuxTrayPolicy {
    enum Target { NONE, MODERN, LEGACY }
    private LinuxTrayPolicy() { }
    static Target target(boolean modern, boolean legacy) {
        return modern ? Target.MODERN : legacy ? Target.LEGACY : Target.NONE;
    }
    static int retrySeconds(int failures) {
        return Math.min(30, 1 << Math.min(5, Math.max(0, failures)));
    }
}
```

```java
package dev.jasper.app.platform;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.util.List;

final class LinuxTrayMenu {
    record Row(int id, String label, boolean separator) { }
    private static final List<Row> ROWS = List.of(new Row(1, "Open Jasper", false),
        new Row(2, "New Window", false), new Row(3, "Settings\u2026", false),
        new Row(4, "", true), new Row(5, "Quit Jasper", false));
    private LinuxTrayMenu() { }
    static List<Row> rows() { return ROWS; }
    static void invoke(int id, ResidentTray.Actions actions) {
        switch (id) {
            case 1 -> actions.open().run();
            case 2 -> actions.newWindow().run();
            case 3 -> actions.settings().run();
            case 5 -> actions.quit().run();
            default -> { }
        }
    }
    static byte[] argb(BufferedImage image) {
        ByteBuffer bytes = ByteBuffer.allocate(Math.multiplyExact(4,
            Math.multiplyExact(image.getWidth(), image.getHeight())));
        for (int y = 0; y < image.getHeight(); y++)
            for (int x = 0; x < image.getWidth(); x++) bytes.putInt(image.getRGB(x, y));
        return bytes.array();
    }
}
```

- [x] **Step 4: Run** the Step 2 command; expect zero failures.
- [x] **Step 5: Commit** `feat: define Linux tray actions and capability policy` with trailer.

### Task 4: Export a real StatusNotifier item and DBusMenu

**Files:** Create `LinuxTrayDbus.java`, `LinuxStatusNotifier.java`, `LinuxStatusNotifierTest.java`, `LinuxTrayWireTest.java`; use `LinuxTestBus`. Gate: Task 1 passed and library binding revalidated.

**Interfaces:** Adapter `LinuxStatusNotifier(String address, Consumer<Host> changed)`; `void start()`, `boolean publish(Host host, IntConsumer action)`, `void withdraw()`, `close()`. `Host(String service, String owner, boolean registered)` is immutable and identifies the watcher owner, not Jasper's owner. `start` subscribes before taking a snapshot; `changed` reports availability (including none) on the controller's serialized queue. `publish` succeeds only after export and registration against that same owner; it returns false on bounded failure. `withdraw` unexports and closes a separate publication connection so watcher registration disappears without needing a nonstandard unregister method. Discovery has its own connection. D-Bus types remain in these two platform files.

- [x] **Step 1: Add wire tests first.** Use `LinuxTestBus`, two independent client connections, a test-only watcher exporting the real watcher interface, and exported objects queried through `org.freedesktop.dbus.interfaces.Properties` plus `org.freedesktop.dbus.interfaces.Introspectable`. A recording action `IntConsumer` is sufficient; no application windows. Exact assertions:

```java
assertThat(itemProperties.GetAll("org.kde.StatusNotifierItem").get("Category").getValue())
    .isEqualTo("ApplicationStatus");
assertThat(itemProperties.GetAll("org.kde.StatusNotifierItem").get("Status").getValue())
    .isEqualTo("Active");
assertThat(itemProperties.GetAll("org.kde.StatusNotifierItem").get("ItemIsMenu").getValue())
    .isEqualTo(false);
assertThat(menuProperties.GetAll("com.canonical.dbusmenu").get("Version").getValue())
    .isEqualTo(new org.freedesktop.dbus.types.UInt32(3));
assertThat(menu.GetLayout(0, -1, new String[0]).revision).isEqualTo(new org.freedesktop.dbus.types.UInt32(1));
assertThat(menu.GetLayout(0, 0, new String[0]).layout.children).isEmpty();
menu.Event(1, "clicked", new org.freedesktop.dbus.types.Variant<>(0), new org.freedesktop.dbus.types.UInt32(0));
assertThat(actions.poll(2, java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(1);
menu.Event(99, "clicked", new org.freedesktop.dbus.types.Variant<>(0), new org.freedesktop.dbus.types.UInt32(0));
assertThat(actions.poll(100, java.util.concurrent.TimeUnit.MILLISECONDS)).isNull();
```

Test names: `publishesStandardPropertiesAndMenu`, `getLayoutHonorsDepthAndPropertyFilter`, `groupEventsReportUnknownIds`, `pixmapsAndEmptyArraysHaveExactSignatures`, `watcherWithoutHostDoesNotPublish`, `subscribesBeforeInitialSnapshot`, `ownerReplacementRejectsOldRegistration`, `withdrawDropsThePublicationName`, `unresponsiveWatcherTimesOut`. Implement the fixture in the test file using the wire interfaces below; registration records the caller's unique name/path and exposes `IsStatusNotifierHostRegistered` as a mutable test property. Use latches for races and wait for the bus's name-owner event on withdrawal. Do not merely call the server Java methods from the same object.

- [x] **Step 2: Run** `./gradlew :jasper-app:test --tests '*LinuxStatusNotifierTest' --tests '*LinuxTrayWireTest'`; expect missing exports/classes or failed wire assertions.
- [x] **Step 3: Implement the wire contract.** Declare annotated interfaces and `@Position` structs/tuples in `LinuxTrayDbus.java`. Public nested classes are required by dbus-java reflection; do not expose them outside the platform package in application signatures. The following is the complete protocol contract to implement (Java carriers use `UInt32`, `DBusPath`, `Variant<?>`, `Struct`, `Tuple`, and `DBusInterface` from the pinned dependency). Preserve capitalization of wire method names:

| Interface/object | Method/property | Wire signature and behavior |
|---|---|---|
| `org.kde.StatusNotifierWatcher`, `/StatusNotifierWatcher` | `RegisterStatusNotifierItem` | `s → ()`; send Jasper's unique publication service name after exporting `/StatusNotifierItem` |
| same and freedesktop watcher variant | `IsStatusNotifierHostRegistered` | read-only `b`; host-present required |
| watcher | `StatusNotifierHostRegistered`, `StatusNotifierHostUnregistered` | signals with no arguments; resnapshot host state |
| bus daemon | `NameOwnerChanged` | `sss`; owner-scoped rediscovery, subscribe before query |
| `org.kde.StatusNotifierItem`, `/StatusNotifierItem` | `Activate`, `SecondaryActivate`, `ContextMenu` | `ii → ()`; Activate dispatches ID 1, SecondaryActivate no-op, ContextMenu no-op because the host has Menu |
| item | `Scroll` | `is → ()`, no-op |
| item | `Category`, `Id`, `Title`, `Status` | `s`: ApplicationStatus, Jasper, Jasper, Active |
| item | `WindowId` | `u`: 0 |
| item | `IconName`, `OverlayIconName`, `AttentionIconName`, `AttentionMovieName` | `s`: empty; IconPixmap is authoritative |
| item | `IconPixmap` | `a(iiay)` for 16, 22, 24, 32, 48 and 64 px rendered from `ApplicationIcon.images("linux")` |
| item | `OverlayIconPixmap`, `AttentionIconPixmap` | typed empty `a(iiay)` |
| item | `ToolTip` | `(sa(iiay)ss)`: empty icon name, icon pixmaps, Jasper, Jasper is running in the background |
| item | `ItemIsMenu`, `Menu` | `b`: false; `o`: `/MenuBar` |
| item | `NewTitle`, `NewIcon`, `NewAttentionIcon`, `NewOverlayIcon`, `NewToolTip`, `NewStatus` | first five: no arguments; NewStatus: `s`; static properties need no periodic signals |
| `com.canonical.dbusmenu`, `/MenuBar` | `Version`, `TextDirection`, `Status`, `IconThemePath` | `u=3`, `s=ltr`, `s=normal`, typed empty `as` |
| menu | `GetLayout` | `iias → u(ia{sv}av)`; revision 1, root ID 0, flat rows 1–5; depth 0 excludes children, -1 includes all; reject below -1 |
| menu | `GetGroupProperties` | `aias → a(ia{sv})`; empty IDs means all rows; filter names, empty filter means all properties |
| menu | `GetProperty` | `is → v`; unknown ID/property returns appropriate D-Bus error |
| menu | `Event` | `isvu → ()`; known actionable ID and `clicked` dispatches once; ignore others |
| menu | `EventGroup` | `a(isvu) → ai`; dispatch valid events; return IDs that cannot be found |
| menu | `AboutToShow` | `i → b`; false (static menu) |
| menu | `AboutToShowGroup` | `ai → aiai`; no updated IDs, missing IDs in errors |
| menu | `LayoutUpdated`, `ItemsPropertiesUpdated`, `ItemActivationRequested` | `ui`, `a(ia{sv})a(ias)`, `iu`; no polling or revision changes for static menu |

Each normal row has `label:s`, `enabled:b=true`, `visible:b=true`; row 4 has `type:s=separator` and visible true.
Root has `children-display:s=submenu`. Filter names exactly; do not return unrequested properties. Use
`Variant` with explicit signatures for empty lists and compound values, never inferred raw `List` signatures.
All properties are read-only: `Set` throws the pinned library's `PropertyReadOnly`; unknown interface/property
throws `UnknownInterface`/`UnknownProperty`. Assert error replies over the wire, not just local exceptions.
Introspection must agree with actual returned variants.

Complete property routing methods for each exported object, using its immutable `Map<String,Variant<?>> values`
and its exact `interfaceName` field:

```java
@Override public Map<String, Variant<?>> GetAll(String requested) {
    if (!interfaceName.equals(requested))
        throw new org.freedesktop.dbus.errors.UnknownInterface("Unknown interface: " + requested);
    return values;
}
@Override @SuppressWarnings("unchecked") public <A> A Get(String requested, String property) {
    Variant<?> value = GetAll(requested).get(property);
    if (value == null)
        throw new org.freedesktop.dbus.errors.UnknownProperty("Unknown property: " + property);
    return (A) value; // Ruling 12: retain the explicit compound/empty variant signature.
}
@Override public <A> void Set(String requested, String property, A value) {
    throw new org.freedesktop.dbus.errors.PropertyReadOnly("Read-only property: " + property);
}
```

Use the pinned library's error classes above and record their actual on-wire names in the wire fixture. The
inspected 5.2.0 `Error` implementation derives names from exception classes; do not assume `setType` changes them
(it is a deprecated no-op). Host interoperability is established by the private-bus and native-host gates. Use typed
protocol contracts directly, not a generic reflection-based RPC dispatcher.

Discovery prefers a live KDE watcher/host, otherwise a live freedesktop watcher/host. Read properties from the
unique owner name and recheck it after registration. Cache no proxy across owner generations. Declare both watcher
interface names, and export the corresponding item interface alias when using the freedesktop variant. Scope
signals by sender/path and post observations to the controller. Bound connect and outbound calls to two seconds,
close an unhealthy owned connection, and report failure without throwing into Swing. Never auto-launch a bus or use
the system bus. Capture `DBUS_SESSION_BUS_ADDRESS` at launch; accept its supported Unix address entries in order. If absent, try
`unix:path=$XDG_RUNTIME_DIR/bus` only when XDG_RUNTIME_DIR is absolute and the socket exists. Never invoke dbus-launch.
If neither is usable, report unavailable and allow legacy fallback. Retry the captured address; do not switch to a
system bus or a different user session.

- [x] **Step 4: Run** real Linux wire tests using private filesystem and abstract buses. Independently check `gdbus introspect --address <private-test-address> --dest <publication-unique-name> --object-path /StatusNotifierItem` and `/MenuBar` while a fixture is running. Compare signatures to the table; no real tray host needed. A gate mismatch requires updating the conditional binding and banner before proceeding.
- [x] **Step 5: Commit** `feat: export Linux StatusNotifier item and native menu protocol` with trailer, including dependency-backed wire declarations and test fixture.

### Task 5: Recoverable controller with one publication

**Files:** Create `LinuxResidentTray.java`, `LinuxResidentTrayTest.java`; extend `LinuxStatusNotifier` lifecycle tests. No native AWT required for controller tests.

**Interfaces:** `public` is unnecessary: package-private final controller with `static Runnable install(ResidentTray.Actions actions)` returning idempotent cleanup (added in Task 6 after both adapters exist). Inject the following package-private records for deterministic tests; production constructs them from the concrete modern and legacy adapters. The `modern` record's `publish` returns actual registration success, not mere watcher presence.

```java
record Modern(java.util.function.BiPredicate<LinuxStatusNotifier.Host, java.util.function.IntConsumer> publish,
              Runnable withdraw, Runnable close, Runnable reconnect) { }
record Legacy(java.util.function.Predicate<java.util.function.IntConsumer> install, Runnable remove, Runnable close) { }
```

Controller events: `hostChanged(Host host)` (null = no live host), `legacyChanged(boolean supported)`, `connectionLost()`, `connectionRestored()`, `close()`. All events are serialized. Test construction is `LinuxResidentTray(ResidentTray.Actions actions, Modern modern, Legacy legacy, Consumer<Runnable> enqueue, BiConsumer<Duration,Runnable> retry, Consumer<Runnable> edt)`; production uses bounded executors and scheduled callbacks. Model observations/pending work separately from published state. Ruling 14 extends Modern with `reconnect = adapter::start`; the adapter's `Consumer<Boolean>` connection callback routes lost/restored independently of deduplicated host snapshots. Ordinary no-host discovery remains signal-driven. Ruling 15 binds `withdraw = adapter::withdrawAndAwait`: its two-second worker-only confirmation must finish before a fallback add; timeout retains cleanup and refuses replacement until a later confirmation succeeds. Nonblocking `withdraw()` remains available for cancellation. Full failure/lifetime requirements supersede the abbreviated happy-path sketch (Ruling 3), including partial-install cleanup and late support invalidation.

Task 6 owns the production scheduler/probe/EDT composition and must physically cancel/remove the outstanding retry on success, connection restoration and close (the injectable `BiConsumer` only supports logical invalidation). It must suspend the five-second legacy probe while modern publication is healthy. Those production bindings remain pending with Task 2's native AWT gate.

- [x] **Step 1: Write deterministic event tests.** Use recording functions and a manually drained `ArrayDeque<Runnable>` for each queue. The interface tests below define the complete assertions for the critical transition path; `events` is the adapters' shared recording list:

```java
controller.legacyChanged(true);
drain.run();
assertThat(events).containsExactly("legacy.add");
controller.hostChanged(new LinuxStatusNotifier.Host("org.kde.StatusNotifierWatcher", ":1.10", true));
drain.run();
assertThat(events).containsExactly("legacy.add", "legacy.remove", "modern.add :1.10");
controller.hostChanged(new LinuxStatusNotifier.Host("org.kde.StatusNotifierWatcher", ":1.11", true));
drain.run();
assertThat(events).endsWith("modern.remove", "modern.add :1.11");
controller.hostChanged(null);
drain.run();
assertThat(events).endsWith("modern.remove", "legacy.add");
controller.close();
controller.close();
drain.run();
assertThat(events).endsWith("legacy.remove", "modern.close", "legacy.close");
```

Additional named tests and exact outcomes:

- `failedModernRegistrationRestoresLegacy`: remove legacy, modern returns false, restore legacy once, schedule one retry; remain resident.
- `noHostRetainsDiscoveryWithoutPublishing`: neither available produces zero adds; a later host adds once.
- `oldOwnerReplyCannotPublish`: block owner A registration, observe owner B, complete A; A is withdrawn and never replaces B.
- `closedGenerationDropsAlreadyQueuedClick`: queue action to fake EDT, close controller, drain EDT; action count remains zero.
- `queuedClickFromWithdrawnBackendIsIgnored`: switch backend before queued action executes; action count zero.
- `busDisconnectUsesLegacyAndRetriesWithCap`: delays 1,2,4,8,16,30,30 seconds; a stable reconnect resets failures.
- `eventStormQueuesAtMostOneReconcile`: send 10,000 observations before drain; one pending reconciliation and last observation wins.
- `closeDuringBlockedCallReturnsPromptly`: bounded completion; no subsequent export/add/action after the call returns.

- [x] **Step 2: Run** `./gradlew :jasper-app:test --tests '*LinuxResidentTrayTest'`; expect missing controller or failed ordering.
- [x] **Step 3: Implement the serialized state machine.** Use the following exact transition algorithm; it is intentionally independent of dbus-java:

```java
private void reconcile() {
    if (closed.get()) return;
    LinuxStatusNotifier.Host candidate = desiredHost;
    boolean wantsModern = candidate != null && candidate.registered();
    if (wantsModern && candidate.equals(publishedHost)) return;
    if (publishedHost != null) {
        generation.incrementAndGet();
        modern.withdraw().run();
        publishedHost = null;
    }
    if (wantsModern) {
        removeLegacy();
        long ticket = generation.incrementAndGet();
        boolean added = modern.publish().test(candidate, id -> dispatch(id, ticket));
        if (closed.get() || ticket != generation.get() || !candidate.equals(desiredHost)) {
            modern.withdraw().run();
            return;
        }
        if (added) { publishedHost = candidate; failures = 0; return; }
        scheduleRetry();
    }
    if (!legacySupported) { removeLegacy(); return; }
    if (!legacyInstalled) {
        long ticket = generation.incrementAndGet();
        boolean added = legacy.install().test(id -> dispatch(id, ticket));
        if (closed.get() || ticket != generation.get()) {
            if (added) legacy.remove().run();
            return;
        }
        legacyInstalled = added;
    }
}

private void removeLegacy() {
    if (!legacyInstalled) return;
    generation.incrementAndGet();
    legacy.remove().run();
    legacyInstalled = false;
}

private void dispatch(int id, long ticket) {
    edt.accept(() -> {
        if (!closed.get() && ticket == generation.get()) LinuxTrayMenu.invoke(id, actions);
    });
}
```

Fields: `AtomicBoolean closed`, `AtomicLong generation`; volatile `desiredHost` and `legacySupported` are updated
at observation admission, so an in-flight I/O completion can notice a replacement even before the reconcile queue
runs. `publishedHost`, `legacyInstalled`, `failures` belong only to the serial worker. Admission increments generation
when an observation invalidates the currently published owner/backend; do not increment it for duplicate healthy
snapshots. Capture the generation in each installed backend's action callback. A stale registration result always
withdraws its own publication connection, not a new owner's connection.

Use one daemon scheduled executor for controller work with one coalesced reconcile token, one reconnect timer and
one legacy-probe timer; set remove-on-cancel. The I/O adapter must enforce its two-second call bound and connection
close cancellation. A failed call must not create another unbounded worker; one active connection attempt maximum.
Legacy calls are marshaled to EDT with a two-second wait on the worker only. EDT never waits for the worker. If a
legacy add times out but later completes, its generation check removes the stale icon immediately.

Production legacy probing uses 5 seconds while no modern item is published; use public AWT host-change observation
as well if Task 2 proves it reliable. Suspend the periodic probe while modern registration is healthy. Modern host
changes use signals, not polling. Retry timers use `LinuxTrayPolicy.retrySeconds`; there is at most one timer per
purpose. Log a failure once per failed state, and log restoration once; no per-probe warning flood.

`close()` atomically invalidates generation synchronously, cancels timers, and schedules detach/withdraw/connection
close. It does not wait on EDT. Cleanup of each adapter uses `try/finally` so one failure cannot skip the other. All
threads are daemon and cleanup is bounded; late discovery/registration cannot resurrect the icon. Test each injected
failure path, not only the transition algorithm's happy path. Use `LinuxTrayPolicy.target` when determining intended
state; actual state changes only after successful installation.

- [x] **Step 4: Run** `./gradlew :jasper-app:test --tests '*LinuxResidentTrayTest' --tests '*LinuxStatusNotifierTest' --tests '*LinuxTrayPolicyTest'`. Expect zero failures. Use timeouts/latches, not sleeps, for controlled races.
- [x] **Step 5: Commit** `feat: recover Linux tray hosts without duplicate icons` with trailer.

### Task 6: Owned XCB transport, authority and cancellation gate

**Gate:** This task produces a bounded native connection and executable characterization, not a resident icon. Task 7 starts only after the Fedora aarch64 gate passes. x86_64 and Ubuntu 22.04 remain required CI release gates by user direction. A socket timeout that leaves native work alive fails the gate. Authority acquisition uses the approved cooperative limit: one pending daemon worker/authority FD until a filesystem syscall returns, no additional attempt, and no late connect/publication; modern publication and Quit remain responsive.

**Files:** Create `LinuxTrayXcb.java`; tests `LinuxTrayXcbTest.java`, `LinuxX11TestServer.java`, `LinuxX11TrayProbe.java`; native test-only `jasper-app/src/test/native/linux-tray-abi.c`; modify `jasper-app/build.gradle.kts`, `docs/design/theme-engine/migration.csv` only for discovered source rows, and the existing verification report. Production/test Java paths are the platform directories in the file map.

**Interfaces:** `LinuxTrayXcb` is a concrete, worker-confined `AutoCloseable`; native pointers never leave this class. Its nested immutable values are `Display(int number, int screen)`, `Setup(int root, int rootVisual, int rootDepth, int imageByteOrder, List<Visual> visuals, List<PixmapFormat> formats)`, `Visual(int id, int depth, int redMask, int greenMask, int blueMask)`, `PixmapFormat(int depth, int bitsPerPixel, int scanlinePad)`, `Reply(int sequence, byte[] bytes)`, and `Event(int type, int sequence, byte[] bytes)`. Produce `static Display parseDisplay(String)`, `static LinuxTrayXcb connect(Map<String,String> launch, long deadlineNanos, Cancellation cancellation)`, `Setup setup()`, `int newId()`, `int internAtom(String,long deadlineNanos)`, `int selectionOwner(int atom,long deadlineNanos)`, `void barrier(long deadlineNanos)`, `List<Event> poll(long deadlineNanos)`, thread-safe nonblocking `void wakeup()` and `void requestClose()`, and worker-only `void close()`. The binding creates/owns its nonblocking close-on-exec wake pipe before native setup; requestClose atomically forbids later requests, shuts down the protected active socket duplicate and writes one coalesced wake byte. Only the worker calls close/disconnect/free; wakeup/requestClose never free native state. All deadlines use `System.nanoTime()`. Task 7 extends this concrete binding with named core/RENDER operations; it does not introduce a backend interface. `LinuxX11TestServer` is a test-only Unix-socket X11 protocol fixture with `filesystem()` and `abstractSocket()` factories, `address()`, `awaitSetup()`, `pauseReads()`, `resumeReads()`, `drop()`, `bytesRead()` and idempotent `close()`; every fixture wait has a five-second bound.

- [x] **Step 1: Add failing parsing, ABI and lifecycle assertions.** `parseDisplay` accepts `:0`, `:7.2`, `unix:7`, `unix/:7.2`; rejects TCP/SSH forms (`localhost:10.0`, `host:0`, `tcp/host:0`), negative/nondecimal/overflow numbers and screens. Limit display number to 65535 and screen to 255; verify selected screen exists in setup. Preserve filesystem `/tmp/.X11-unix/XN` and abstract `\0/tmp/.X11-unix/XN` candidates, trying each within the same total deadline. Tests load the class without loading native libraries on macOS/headless paths. Use JNA 5.14.0 already present, not a new dependency.

```java
@Test void acceptsOnlyLocalDisplayGrammar() {
    assertThat(LinuxTrayXcb.parseDisplay("unix/:7.2")).isEqualTo(new LinuxTrayXcb.Display(7, 2));
    for (String value : List.of("localhost:10.0", "host:0", ":-1", ":65536", ":1.256", ":1.x"))
        assertThatThrownBy(() -> LinuxTrayXcb.parseDisplay(value)).isInstanceOf(IllegalArgumentException.class);
}
```

Add named test assertions: `authoritySnapshotRejectsFifoDirectorySymlinkRaceAndOversize` (cap 1 MiB, at most 4096 records, 65535 bytes per decoded field, no special-file read); `authorityMatchesLocalThenWild` (FamilyLocal 256 matches captured host bytes/display-number ASCII, FamilyWild 65535 fallback, name exactly `MIT-MAGIC-COOKIE-1`, data exactly 16 bytes; mismatches never reused); `missingCookieUsesEmptyAuthAndServerDecides` (no access-control mutation); `malformedAuthorityFreesEveryNativeRecord`; `deadlineShutdownCannotHitReusedFd`; `onlyWorkerDisconnectsAfterCallReturns`; `failedAttemptMustFinishBeforeRetry`; `nativeLoadFailureIsScoped` (ordinary unavailable, no static initializer failure). Never print record contents/cookies.

`linux-tray-abi.c` includes `<xcb/xcb.h>`, `<xcb/xcbext.h>`, `<xcb/render.h>`, `<X11/Xauth.h>`, `<poll.h>`, `<sys/un.h>` and prints `sizeof`/`offsetof` as `name=value`; the Java probe compares exact values, not architecture names. On Linux x86_64/aarch64 require 8-byte pointers/longs; XIDs, atoms and cookies remain 32-bit. Assert `xcb_void_cookie_t=4`, generic event/error=36, client-message=32, `xcb_auth_info_t=32` with name/data pointers at 8/24, `Xauth=64` with address/number/name/data pointers at 8/24/40/56, `pollfd=8`, `sockaddr_un=110`. Compare every field offset used by the binding with the C output. Decode fixed X protocol buffers at their wire offsets; never map C `long` to XID.

- [x] **Step 2: Run red tests.** `./gradlew :jasper-app:test --tests '*LinuxTrayXcbTest'`; expect missing binding/fixture before implementation. Test regular-file auth parsing on all platforms through byte fixtures; native ABI/socket tests are Linux-only. Required-native mode in Step 5 fails rather than skips missing libraries, compiler, symbols or fixture prerequisites.

- [x] **Step 3: Implement connection/auth and exact JNA boundary.** Lazy-load `xcb`, `xcb-render`, `Xau` and libc when the native worker constructs the attempt-scoped `Cancellation` immediately before `connect` (Ruling 22); class loading and adapter construction remain native-free. Use nested `Library` declarations and JNA `Pointer`, `Memory`, `IntByReference`, `PointerByReference`, `NativeLong`; every cookie is the following by-value type (same representation for core and RENDER):

```java
@Structure.FieldOrder("sequence")
public static class Cookie extends Structure implements Structure.ByValue { public int sequence; }
@Structure.FieldOrder({"namelen", "name", "datalen", "data"})
public static class Auth extends Structure {
    public int namelen; public Pointer name; public int datalen; public Pointer data;
}
interface Xcb extends Library {
    Pointer xcb_connect_to_fd(int fd, Auth auth);
    Pointer xcb_get_setup(Pointer connection);
    int xcb_connection_has_error(Pointer connection);
    int xcb_get_file_descriptor(Pointer connection);
    int xcb_generate_id(Pointer connection);
    int xcb_flush(Pointer connection);
    Pointer xcb_poll_for_event(Pointer connection);
    int xcb_poll_for_reply(Pointer connection, int sequence, PointerByReference reply, PointerByReference error);
    Cookie xcb_get_input_focus(Pointer connection);
    Cookie xcb_intern_atom(Pointer connection, byte onlyIfExists, short length, byte[] name);
    Cookie xcb_get_selection_owner(Pointer connection, int selection);
    void xcb_disconnect(Pointer connection);
}
```

Bind libc `socket`, `connect`, `getsockopt`, `poll`, `fcntl`, `dup`, `shutdown`, `close`, `pipe2`, `read`, `write`, `open`, `fstat(int fd, Pointer stat)`, `fmemopen`, `fclose`, `free`; Xau `XauReadAuth(Pointer)->Pointer`, `XauDisposeAuth(Pointer)`. Use Linux constants `AF_UNIX=1`, `SOCK_STREAM=1`, `SOCK_NONBLOCK=0x800`, `SOCK_CLOEXEC=0x80000`, `SHUT_RDWR=2`, `SOL_SOCKET=1`, `SO_ERROR=4`, `EINPROGRESS=115`, `EAGAIN=11`, `EINTR=4`, `POLLIN=1`, `POLLOUT=4`, `POLLERR=8`, `POLLHUP=16`, `O_NONBLOCK=0x800`, `O_CLOEXEC=0x80000`, `O_NOFOLLOW=0x20000` on x86_64 / `0x8000` on aarch64, `F_DUPFD_CLOEXEC=1030`. ABI helper also checks these constants and all stat fields used. Implement nested `NativeStat` using 256-byte Memory and fstat: x86_64 struct size144 with st_dev0/st_ino8/st_mode24/st_size48; aarch64 size128 with st_dev0/st_ino8/st_mode16/st_size48. Select only os.arch amd64/x86_64 or aarch64/arm64; reject others. Verify these offsets against installed headers in the ABI gate. Read mode as unsigned int, device/inode/size as 64-bit; S_IFMT=0170000/S_IFREG=0100000. The native gate also verifies mtime/ctime seconds/nanoseconds at 88/96/104/112 on both supported stat layouts; snapshot comparison includes these to reject same-size edits. No guessed cross-architecture stat layout.

Capture the environment via `Map.copyOf(System.getenv())` at installer construction. Authority path is captured nonempty `XAUTHORITY`, otherwise captured `HOME + "/.Xauthority"`. Acquire a 1 MiB maximum regular-file snapshot with nonblocking, no-follow, close-on-exec open; verify device/inode/size before and after, reject changed snapshots and special files, and check the shared two-second deadline between reads (16 KiB chunks). Feed the bounded byte snapshot to `fmemopen(...,"rb")`, decode at most 4096 `XauReadAuth` records, deep-copy matching name/data into worker-owned `Memory`, dispose every Xauth and fclose in `finally`. No unbounded `XauGetBestAuthByAddr`, `Files.readAllBytes` or authority scan hidden in a Future. Check cancellation and deadline before and after every filesystem syscall. Acquisition is cooperative: an outstanding pathname/open/stat/read syscall may retain one daemon worker and authority FD until it returns. Invalidate the attempt before the caller times out; never admit another acquisition/retry until worker cleanup finishes, and never connect/publish from its late result. A deterministic held-acquisition test proves admission invalidation, one attempt, no late connect/publication and cleanup after release; it does not claim syscall cancellation. This phase creates no XCB connection/icon, so modern publication remains available and Quit never waits for the daemon. The path rule rejects final-component symlinks; normal local regular files are the operational scope, not an enforced filesystem deadline. Record this limit in verification and final maintenance text.

Open nonblocking Unix socket, handle EINPROGRESS with `poll` and `SO_ERROR`, within `deadlineNanos`; create a duplicate before passing ownership to `xcb_connect_to_fd`. libxcb owns the original thereafter. Poll and XCB setup may change FD blocking mode; duplicate shutdown remains the cancellation mechanism. Keep the auth `Memory` alive until connect returns. Parse setup using validated length fields with a 1 MiB bound; reject unknown image format/invalid screen without leaking the connection.

Wrap **every native operation that can flush**, including setup, request generation, explicit `xcb_flush`, render/image requests and reply polling, in the same operation lifetime. A separately runnable single daemon deadline scheduler owns shutdown, never disconnect/free:

```java
// generation lock protects both fd identity and its lifetime against descriptor reuse.
synchronized (deadlineLock) {
    long ticket = ++operationEpoch;
    operationDuplicate = libc.fcntl(socketFd, F_DUPFD_CLOEXEC, 0);
    if (operationDuplicate < 0) throw new IllegalStateException("XCB cancellation FD unavailable");
    deadlineFuture = deadlines.schedule(() -> {
        synchronized (deadlineLock) {
            if (operationEpoch == ticket && operationDuplicate >= 0)
                libc.shutdown(operationDuplicate, SHUT_RDWR);
        }
    }, Math.max(0, deadlineNanos - System.nanoTime()), TimeUnit.NANOSECONDS);
}
// finally, only after the native call returned:
synchronized (deadlineLock) {
    ++operationEpoch;
    deadlineFuture.cancel(false);
    libc.close(operationDuplicate);
    operationDuplicate = -1;
}
```

The owning native worker alone uses connect/poll/request/close; other threads use only wakeup/requestClose. requestClose also works while connect is in progress through a captured cancellation holder allocated before connect; connect accepts that holder as trailing `Cancellation cancellation` argument. `LinuxTrayXcb.Cancellation` is a concrete nested value owning the generation lock, active duplicate, close flag and wake pipe, created by `new Cancellation()` on the native worker before its first call and published before starting connect. It exposes thread-safe `wakeup()`/`requestClose()` and worker-only `close()`. The adapter can requestClose before publication through its own close flag, which the worker checks before connect. LinuxTrayXcb delegates its two cross-thread methods to this holder. The deadline algorithm above operates on that holder. Use a connection-level protected duplicate while a native call is active; never duplicate a stale original after disconnect. On requestClose synchronously mark closed, invalidate operation epoch/admissions and shutdown the still-protected active duplicate, then wake the worker. A connection not yet published to the adapter is still canceled through the published Cancellation holder. Worker disconnects only after call return and closes all remaining FDs/auth memory in `finally`. Disarm-before-close is mandatory; tests force old deadline execution after FD reuse. `barrier` queues GetInputFocus and polls its reply; collect scoped error sequences up to that barrier. Do not call blocking `xcb_request_check`, `xcb_wait_for_event` or reply wait APIs. Cap outstanding reply cookies at 64, event batches at 64 events/256 KiB and total copied reply at 1 MiB; reject an excessive reply using its header before copying. Free each event/reply/error once. Drain batches then service cleanup before ordinary commands.

- [x] **Step 4: Implement the opt-in deterministic native gate.** Add Gradle `linuxX11TrayProbe` as `JavaExec` on test runtime classpath, depending on `testClasses`, main class `dev.jasper.app.platform.LinuxX11TrayProbe`, native access enabled; ordinary `check` never launches it. Probe options are `--gate transport|legacy|popup`, `--output <directory>`, `--require-native`, `--display <local-display>`, `--panel lxde|xfce` (latter options needed by Tasks 7–8). Transport mode creates only its own Unix fixture sockets; it never opens a GUI or uses the desktop bus. Create `linuxTrayNativeTest` Gradle `Test` on the existing test source set, include XCB/legacy tests with `jasper.tray.requireNative=true`; no skipped required-native row. Build the ABI helper only for Linux native mode with system `cc` and public headers; it only evaluates sizeof/offsetof/constants and requires no native library linking. Prefer `pkg-config --cflags xcb xcb-render xau`; if development headers are absent on the authorized Fedora host, use the rootless extraction below, never a sudo/system installation. No SDK or shipped native output.

Run both socket forms against fixture modes: no setup response, setup accepted then no replies, stop reading until explicit flush blocks, stop reading during request generation until an **implicit** flush blocks, invalid cookie/BadWindow/BadMatch, EOF, close in every phase. Execute one deterministic stalled attempt per distinct phase with a two-second deadline and 500 ms cleanup allowance, then four fast fail/reconnect/close cycles and 32 fast FD-reuse iterations. Assert no overlapping attempts, every socket worker completes, zero live duplicate sockets/auth buffers afterward, and thread and `/proc/self/fd` counts return to baseline. For held authority acquisition, assert the sole worker remains owned while held, then completes cleanup after release; never count caller timeout as syscall completion. Do not repeat 100 two-second timeouts; the phase acknowledgement proves the relevant path. Use latch/state acknowledgements to prove each native phase was entered; a test that never backpressures a flush is failure. Run the 32 FD-reuse iterations under a delayed old cancellation callback and prove the replacement socket remains usable. Use a separate watchdog process with 10-second bound per case to record JVM survival; a timed-out child is failed evidence, not a pass. This process is a test fixture, never a production helper.

- [x] **Step 5: Run and record the gate before Task 7.** Locally run `./gradlew :jasper-app:test --tests '*LinuxTrayXcbTest' :jasper-app:compileTestJava` and architecture/source-inventory checks. On authorized Fedora host `dustin@10.211.55.13`, work only under `/home/dustin/jasper-tray-gate.1yPuL3` with pinned JBR b635.75:

```bash
./gradlew :jasper-app:linuxTrayNativeTest --tests '*LinuxTrayXcbTest'
./gradlew :jasper-app:linuxX11TrayProbe --args='--gate transport --require-native --output build/tray-gate/transport'
```

If headers are absent, execute rootless extraction in the authorized remote workspace before the native gate (installed runtime versions select exact matching development RPMs):

```bash
mkdir -p build/tray-gate/headers/rpms build/tray-gate/headers/root
xcbVersion=$(rpm -q --qf '%{VERSION}-%{RELEASE}.%{ARCH}' libxcb)
xauVersion=$(rpm -q --qf '%{VERSION}-%{RELEASE}.%{ARCH}' libXau)
dnf download --destdir build/tray-gate/headers/rpms "libxcb-devel-$xcbVersion" "libXau-devel-$xauVersion" xorg-x11-proto-devel
```

Use `rpm2cpio` piped to `cpio -idm --no-absolute-filenames` with the extraction root as working directory, one downloaded RPM at a time; validate `rpm -qp` identity against the requested installed version before extraction. Pass `-I<workspace>/build/tray-gate/headers/root/usr/include` to `cc` and run `cc -std=c11 ... linux-tray-abi.c -o .../linux-tray-abi` (no `-l` flags). Prefer repo-provided libxcb/Xau header RPMs matching installed versions; if the exact RPM or rootless download command is unavailable, record a concrete missing-header blocker, not a permission request for system installation. Header RPMs are test-only and are not bundled. CI installs dev prerequisites normally.

Record C/Java ABI output, libxcb/libXau/JBR versions, every timeout/leak counter and exact command in verification. Gate passes only with zero required skips, zero stuck socket attempts, authority cleanup after fixture release, zero unexpected exceptions and all cancellation assertions satisfied. The isolated panel authorization extends to Tasks 7–8 probes; active Plasma, real Jasper GUI, AOT and benchmarks remain excluded. Commit `feat: add bounded owned XCB tray transport` with required trailer after per-task review/fixes. Gate failure stops dependent work and is recorded explicitly in STATUS.

### Task 7: XEmbed legacy adapter, drawing and native host gate

**Files:** Create `LinuxLegacyTray.java`, `LinuxLegacyTrayTest.java`; extend `LinuxTrayXcb.java`, `LinuxTrayXcbTest.java`, `LinuxX11TestServer.java`, `LinuxX11TrayProbe.java` and verification. Add inventory rows only if discovery requires them. All Java paths are the platform paths in the map.

**Interfaces:** Produce `LinuxLegacyTray(Map<String,String> launch, Consumer<Boolean> available, Consumer<MenuRequest> menu, Runnable cancelPopup)`, `void start()`, `boolean install(IntConsumer action)`, `void removeAndAwait()`, `void discoveryEnabled(boolean)`, `void close()`. Constructor captures values without native loading. Nested `MenuRequest(long epoch, Rectangle rootBounds, boolean pointer, IntConsumer action, BooleanSupplier valid)` carries a defensive rectangle copy in X root device pixels; `valid` checks adapter admission as well as connection/host/icon epoch. Its action is already adapter-guarded and will also pass through the controller guard. Availability callbacks are delivered outside native locks. Only the native worker calls XCB/free; install/removal run on the controller worker with a **two-second caller deadline including admission**. `false` guarantees no icon or pending install survives. Cleanup still owed throws `IllegalStateException`; it never returns false to permit another publication. `close` invalidates synchronously and returns without waiting. Authority-only timeout invalidates its attempt before returning unavailable and cannot later connect/publish; its sole daemon worker retains the acquisition slot until filesystem return, without blocking modern publication or Quit. Socket/icon cleanup obligations still refuse replacement until confirmed.

Extend concrete `LinuxTrayXcb` with `void select(int window,int mask,long deadline)`, `int property32(int window,int atom,int type,long deadline)` (0 for absent, reject unexpected count/format/type), `int[] property32s(int window,int atom,int type,int max,long deadline)`, `void property32(int window,int atom,int type,int[] data,long deadline)`, `void property8(int window,int atom,int type,byte[] data,long deadline)`, `int parent(int window,long deadline)`, `Rectangle geometryOnRoot(int window,long deadline)`, `int createIcon(int parent,Visual visual,int colormap,int size,long deadline)`, `void sendClientMessage(int destination,int window,int type,int eventMask,int... data)`, `void destroyIcon(long deadline)`, `void paint(BufferedImage image,int width,int height,Visual visual,boolean alpha,long deadline)`, `int[] keysyms(int firstKeycode,int count,long deadline)`. Methods that queue requests receive/retain the current operation deadline even when their signature omits it (`sendClientMessage` runs only inside an armed operation). `destroyIcon` destroys the complete owned resource set and completes a reply barrier. No Pointer/XID reaches app code.

- [x] **Step 1: Write failing epoch, docking, geometry and pixel tests.** Add named test methods with these exact assertions; fixture commands acknowledge each race before releasing it:

| Test | Required assertions |
|---|---|
| `subscribesBeforeInitialOwnerSnapshot` | root event selection precedes owner request; manager arriving between both results in one current owner |
| `requiresHandshakeReparentAndCurrentOwner` | request dock alone=false; embedded-notify alone=false; reparent alone=false; both plus current owner=true; icon is unmapped before acceptance |
| `sameXidNewConnectionIsNewHost` | connection epoch changes even with identical owner XID; false then true callbacks; old actions count zero |
| `coalescedFalseTrueRefreshesLegacyGeneration` | deliver false/true before controller drain; installed callback invalidates and old callback count zero; one redock |
| `ownerLossDuringDockNeverLeavesPendingIcon` | false only after destroyed/barrier or terminated connection; refused cleanup throws and blocks replacement |
| `hostLossBeforeQueuedActionDropsIt` | primary release then owner loss before EDT drain produces zero callbacks |
| `boundedCommandSlotPrioritizesCleanup` | 10,000 probes/resizes leave one pending normal command, one cleanup flag and bounded image bytes; close wins |
| `configuredPixelsDoNotUseJbrScale` | actual 24x40 slot remains 24x40 at JBR scale 1 and 2; artwork centered with aspect ratio |
| `unsupportedSlotOrVisualFailsClosed` | 0, 513, unsupported masks/padding/depth or parent-depth mismatch produce unavailable with no mapped stray window |
| `nativePixelsArePremultipliedAndMaskEncoded` | half-alpha red ARGB `0x80ff0000` gives premultiplied red 128, not 255; tests cover 16-bit RGB565, depth24/bpp32, alpha32, byte orders 0/1, 16/32 scanline padding |
| `resizeExposeAndBackgroundOnlyRedrawWhenDirty` | burst of 10,000 events makes one paint of final dimensions; next idle drain makes zero paints |
| `inputAndTraversalUseCurrentEpoch` | Button1 release inside=action1 once; Button3 release=one menu request; press/middle/wheel/outside=zero; Return/Space=1, Menu/Shift-F10=menu; Tab/Shift-Tab=focus-next/previous, no grab |

```java
@Test void premultipliesNativePixelsWithoutChangingDbusEncoding() {
    BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
    image.setRGB(0, 0, 0x80ff0000);
    assertThat(LinuxTrayXcb.pixels(image, 32, 32, 0, 0x00ff0000, 0x0000ff00, 0x000000ff, 0xff000000))
        .containsExactly((byte) 0, (byte) 0, (byte) 128, (byte) 128);
    assertThat(LinuxTrayMenu.argb(image)).containsExactly((byte)128, (byte)255, (byte)0, (byte)0);
}
```

`pixels(BufferedImage,int bitsPerPixel,int scanlinePad,int byteOrder,int redMask,int greenMask,int blueMask,int alphaMask)` is a package-private pure converter in `LinuxTrayXcb`; it rejects noncontiguous/overlapping masks and unsupported bpp (support 16/24/32). Round premultiplication `(channel * alpha + 127) / 255`, scale channels to mask bit width with rounding, write bytes in server order, and zero row padding. Cap width/height at 512 and owned upload buffers at 4 MiB including source/destination/padding.

- [x] **Step 2: Run red tests.** `./gradlew :jasper-app:test --tests '*LinuxLegacyTrayTest' --tests '*LinuxTrayXcbTest'`; expect missing adapter/converter. Keep Task 5's existing false/true regression unchanged and add the adapter-to-controller case above.

- [x] **Step 3: Implement the native owner and XEmbed state machine.** One daemon `jasper-tray-xcb` worker plus Task 6's independent daemon deadline scheduler; one coalesced normal-command slot and priority cleanup bit. Task 6 owns the nonblocking `pipe2` wakeup; adapter submits call `xcb.wakeup()`/the shared Cancellation holder and never closes wake FDs itself. Install waits only off EDT and shares its caller's deadline across queueing/connect/snapshot/dock/barrier. The worker has states disconnected, discovering, docking, embedded, cleanup-owed, closed. `start` admits discovery; `discoveryEnabled(false)` cancels owner-check work after confirmed removal while preserving passive wake/event polling. Resume performs fresh snapshot before install. Reconnect never begins until prior worker cleanup finishes.

Root mask is `StructureNotifyMask=1<<17`; owner mask is `(1<<17)|(1<<22)`; icon mask is KeyPress `1`, ButtonPress `4`, ButtonRelease `8`, Exposure `1<<15`, StructureNotify `1<<17`, FocusChange `1<<21`, PropertyChange `1<<22`. Subscribe before snapshot, then intern `_NET_SYSTEM_TRAY_S` + selected screen, `MANAGER`, `_NET_SYSTEM_TRAY_OPCODE`, `_NET_SYSTEM_TRAY_VISUAL`, `_NET_SYSTEM_TRAY_ORIENTATION`, `_XEMBED`, `_XEMBED_INFO`, `WM_NAME`, `WM_CLASS`, `_NET_WM_NAME`, `UTF8_STRING`, `_NET_WM_ICON`, `WM_NORMAL_HINTS`, `WM_SIZE_HINTS`, `VISUALID`, `CARDINAL`. Never select SubstructureRedirect or modify another window. Client messages are format32 and exactly five int words; event type is `rawType & 0x7f`. Validate target/type/format and all resource epochs, including synthetic events.

Capture host identity `(connectionEpoch, screen, ownerXid, hostEpoch)`; increment hostEpoch on invalidation, visual changes and owner replacement. Atom data and host/root geometry are connection-scoped. On changed/invalid owner: invalidate action epoch **first**, invoke cancelPopup, admit `available(false)`, remove old icon, validate new owner/visual, then admit `available(true)`. Deliver false/true as separate callbacks even if immediately adjacent. Observe DestroyNotify=17, UnmapNotify=18, MapNotify=19, ReparentNotify=21, ConfigureNotify=22, PropertyNotify=28, SelectionClear=29, ClientMessage=33; verify owner by fresh selection snapshot, never merely by XID existence. While enabled, five-second owner snapshots cover owner selection loss without destruction; they never reinstall a healthy icon.

Create icon unmapped with visual/depth/colormap, background chosen by rendering rules below, `WM_NAME`/UTF8 name `Jasper`, `WM_CLASS` bytes `jasper\0Jasper\0`, `_NET_WM_ICON` existing image ARGB CARDINALs, and minimum 2x2/preferred 24x24/maximum 512x512 size hints (Ruling 25; XFCE treats 1x1 as hidden). `_XEMBED_INFO` is type `_XEMBED_INFO`, format32, `[0,1]` (version0/mapped intent). Send docking to the **captured owner**, window=owner, message `_NET_SYSTEM_TRAY_OPCODE`, data `[serverTimestamp,0,iconXid,0,0]`, NoEventMask. A timestamp of CurrentTime=0 is allowed before a real server event timestamp is available; later use the most recent server timestamp. Accept `_XEMBED` EMBEDDED_NOTIFY=0 only with data1=0, data3=nonzero embedder and negotiated version0, matching icon; QueryTree must confirm that parent equals announced embedder, and a final selection snapshot must still match owner. Map only after these validations. Do not treat a root reparent or an unrelated configure as embedding.

Support XEmbed WINDOW_ACTIVATE=1/DEACTIVATE=2, REQUEST_FOCUS=3, FOCUS_IN=4/OUT=5, FOCUS_NEXT=6/PREV=7, MODALITY_ON=10/OFF=11. Request focus through current embedder on primary/key interaction, honor focus messages, return traversal to embedder; no XSetInputFocus/global grab. Track _XEMBED_INFO mapped flag and negotiated version; host unmapping pauses interaction, remapping repaints. Resolve the server's min/max keycode setup range (max256 codes; max32 keysyms/code) using GetKeyboardMapping; refresh on MappingNotify=34. Keysyms Return=0xff0d, Space=0x20, Menu=0xff67, F10=0xffc7 with ShiftMask=1, Tab=0xff09/ISO_Left_Tab=0xfe20. Deliver release-only primary action at valid local coordinates; duplicate/malformed event sequences do not double-invoke.

- [x] **Step 4: Implement native drawing and confirmed cleanup.** Extend lazy JNA declarations for core unchecked create/change/destroy window (Ruling 24; sequences retained and errors drained through the barrier), colormap, pixmap/GC, ChangeProperty, GetProperty, QueryTree, GetGeometry, TranslateCoordinates, Map/Unmap, SendEvent, PutImage, GetKeyboardMapping, and RENDER QueryVersion/QueryPictFormats/CreatePicture/FreePicture/Composite. Bind each with exact C signature from installed public headers and Task 6 ABI helper; retain sequence numbers and use the reply barrier/error queue, not `xcb_request_check`. Fixture tests assert encoded opcode/length/values. Cap request payload to setup maximum request size; tile PutImage by scanline when necessary, including header/padding in the limit; do not require BIG-REQUESTS.

Resolve advertised visual against setup and RENDER direct-format channel shifts/masks (not an assumed BGRA visual). Absent property uses root visual. Alpha visual requires a usable RENDER alpha destination and the host's verified compositing path; transparent Clear operator0 then Over operator3. For opaque destination matching embedder depth, set ParentRelative background (value1), ClearArea before repaint, composite premultiplied ARGB32 source Over. Reject ParentRelative across depth mismatch. Allocate source pixmap/GC/picture and destination picture/optional colormap once per geometry/visual epoch; free old set after replacement barrier. Native dimensions are already device pixels. Rasterize `ApplicationIcon.images("linux")` offscreen through `ResidentTray.image` into the slot with preserved aspect ratio and centered full artwork; no UI transform multiplication. Expose/configure/parent-background signals only set one dirty bit. Visual property change invalidates and redocks exactly once.

`removeAndAwait`: invalidate native action epoch, cancel popup, request priority teardown; destroy icon and free pictures/pixmaps/GC/colormap; queue barrier and wait within the original two seconds. A destruction error is scoped and confirmed connection termination is acceptable; a timeout triggers protected-socket shutdown and waits for worker-owned disconnect confirmation within that same overall bound. If cleanup remains unconfirmed, throw and retain cleanup-owed; never signal successful removal. Close must remain nonblocking while worker cleanup drains. Native resource counters/test callbacks prove one free per allocation and no free while a native call is outstanding.

- [x] **Step 5: Execute the authorized isolated real-panel gate.** Extend `LinuxX11TrayProbe --gate legacy` to drive the adapter directly, capture uncaught exceptions as failed rows, and record host epochs/resources/slot dimensions/actions without cookies. Test harness controls only panels it starts inside the isolated display/bus; panel/app PID files must be scoped below output. With the user-authorized Fedora prerequisites, execute in `/home/dustin/jasper-tray-gate.1yPuL3`:

```bash
./gradlew :jasper-app:linuxTrayNativeTest --tests '*LinuxTrayXcbTest' --tests '*LinuxLegacyTrayTest'
dbus-run-session -- xvfb-run -a -s '-screen 0 1280x800x24' ./gradlew :jasper-app:linuxX11TrayProbe --args='--gate legacy --panel lxde --require-native --output build/tray-gate/lxde'
dbus-run-session -- xvfb-run -a -s '-screen 0 1280x800x24' ./gradlew :jasper-app:linuxX11TrayProbe --args='--gate legacy --panel xfce --require-native --output build/tray-gate/xfce'
```

Harness launches `lxpanel` or `xfce4-panel --disable-wm-check` using private XDG_CONFIG_HOME, after an initial no-host interval and an acknowledged unavailable snapshot. Use copied minimal panel profiles with only the tray plugin; record installed plugin identifiers/config, not active Plasma configuration. Run 20 slow/fast panel stop/start cycles, actual live tray applet removal/re-add via that isolated panel's configuration, selection replacement without process death using fixture owner, owner loss during docking and menu request. Distinguish whole-Xvfb teardown from loss of the adapter's independent socket. Assert one icon, no undocked visible window, old resources removed before replacement, no unexpected uncaught exceptions/incidents, no action from old host. Missing panel/program is failure in require-native mode.

Capture light/dark/patterned panels at 16/24/32/48 slots, opaque/alpha visual paths with compositor off and, if available, on (private `xcompmgr`), matching 1x/2x and mixed host/JBR scales (`-Dsun.java2d.uiScale=2` for the probe process). Compare screenshots for complete artwork/corners/no trails, not only successful API returns. An alpha-advertising host without usable compositing must decline cleanly rather than paint black corners. xcompmgr is an optional test tool, not a new system-install prerequisite; if absent, record compositor-on/alpha-visible rows pending while observed opaque/noncomposited host paths can pass. Obtain extra compositor tooling only by rootless RPM extraction in the isolated directory if needed; never install system packages without authorization. A pending alpha path cannot be enabled as supported production behavior without its visual gate. Record input and XEmbed focus/traversal through `xdotool` only on this display. Virtual scale evidence leaves physical mixed-monitor rows pending for Task 10. Gate failure stops Task 9; fix/amend, never substitute a protocol fixture for real panels. Commit `feat: implement owned XEmbed legacy tray` with trailer after review.

### Task 8: Transient Swing popup and placement gate

**Files:** Create `LinuxTrayPopup.java`, `LinuxTrayPopupTest.java`; extend `LinuxX11TrayProbe.java`, verification and required inventory rows. No permanent frame or workspace window.

**Interfaces:** `LinuxTrayPopup(Consumer<Runnable> edt)`, `void request(LinuxLegacyTray.MenuRequest request)`, `void cancel()`, `void close()`. All public-to-package methods admit nonblocking work; only EDT creates/disposes Swing. Keep one pending request using atomic latest request + one queued EDT drain; close/host invalidation clears it. Pure nested `Screen(Rectangle deviceBounds, Rectangle userBounds, Insets insets, AffineTransform transform)` plus `static Rectangle toUser(Rectangle root, Screen screen)`, `static Point clamp(Point anchor, Dimension menu, Screen screen)` for deterministic headless tests. Ruling 26 adds defensive-copy `MenuRequest.rootScreenBounds` from selected X11 setup width/height; only a proven single-device identity is initially admitted. Native root monitor rectangles must be paired with public GraphicsConfiguration identities by verified root/device rectangle; ambiguous pairing refuses keyboard popup and logs unavailable once rather than guessing a global scale.

- [x] **Step 1: Write red placement and lifetime tests.** `toUser` uses screen origin and inverse transform on the **relative device vector**, not inverse-transforming a global X root coordinate:

```java
// Native monitor2 starts at x=1920; AWT monitor2 user-space starts at x=1920, scale2.
var screen = new LinuxTrayPopup.Screen(new Rectangle(1920,0,2560,1440),
    new Rectangle(1920,0,1280,720), new Insets(0,0,40,0), AffineTransform.getScaleInstance(2,2));
assertThat(LinuxTrayPopup.toUser(new Rectangle(2120,100,40,40), screen))
    .isEqualTo(new Rectangle(2020,50,20,20));
assertThat(LinuxTrayPopup.clamp(new Point(3190,710), new Dimension(240,200), screen))
    .isEqualTo(new Point(2960,480));
```

Add cases negative monitor origins, fractional1.25/1.5 transforms (floor minimum/ceil maximum), menu larger than work area (pin to work-area origin), all four screen edges/insets, moved pointer, missing MouseInfo/headless, mismatched/ambiguous native-to-AWT monitor identity (no popup). Test `tenThousandRequestsQueueOneEdtTask`, `invalidEpochBeforeEdtCreatesNoWindow`, `hostLossCancelsAndDisposesOnce`, `closeDropsQueuedSelection`, `quitCanceledDoesNotCloseAdapter` and all menu row IDs/order against shared rows. Inject recording EDT and a package-private `Consumer<MenuRequest>` creation seam for headless admission tests; the production constructor supplies the real Swing creation method. Do not invoke real windows in `check`.

- [x] **Step 2: Run** `./gradlew :jasper-app:test --tests '*LinuxTrayPopupTest'`; expect missing implementation.
- [x] **Step 3: Implement short-lived menu ownership and mapping.** `request` samples latest guarded request; EDT checks `valid` before creating and before invoking each action. Reuse `LinuxTrayMenu.rows`, with labels/IDs unchanged; selection calls captured `IntConsumer` then disposes in `finally`. Create `new JDialog((Window)null)`, undecorated, focusable, `Window.Type.UTILITY`, focusableWindowState true, controlled DO_NOTHING_ON_CLOSE admission so cleanup retains the peer through any admitted native operation; place a `JPopupMenu` in its content pane with normal Swing menu selection/keyboard support. After pack obtain only this peer ID through public JNA, submit bounded existing-XCB skip-taskbar/skip-pager hint preservation and barrier, retain the peer until native completion even after cancellation, then guard EDT show. EDT never waits; failure never shows and disposal waits for operation completion/connection termination to prevent XID reuse. Pack before clamping, show once, request focus/menu selection; never construct a JFrame. Dispose on action, Escape, outside press, focus loss, invalidation or close. Remove AWTEventListener, WindowFocusListener, menu listeners and clear MenuSelectionManager path during the same idempotent dispose; no Timer/listener keeps a window alive. The native icon holds no pointer/keyboard grab.

Pointer invocation samples `MouseInfo.getPointerInfo` on EDT; use its device GraphicsConfiguration bounds/insets and user-space point if still within the verified icon root rectangle transformed to that screen. Otherwise use icon bottom-center mapped through `toUser`; keyboard invocation always uses that path. Use only the approved public MouseInfo/GraphicsConfiguration path; no additional native library. Match the X root rectangle to public configurations only when origin/extent/default-transform pairing is unique and verified by the probe (single-monitor mapping is the initial provable case). For a multi-monitor layout that cannot establish an unambiguous correspondence from public data, decline the keyboard/moved-pointer anchored popup rather than guessing a root origin or global scale. Pointer placement still uses the current MouseInfo device/user coordinates; the gate must prove whether the pointer remained over the icon before using this route. Tests pin the mapping arithmetic above; native physical evidence pins identity. A pointer that moved and lacks a proven icon mapping cancels the popup, with the next user request still allowed. Physical mixed-DPI acceptance remains pending until observed. Sample pointer only at display time, never carry raw X button coordinates into Swing.

- [x] **Step 4: Run headless tests and isolated popup acceptance.** `./gradlew :jasper-app:test --tests '*LinuxTrayPopupTest' --tests '*LinuxLegacyTrayTest' --tests '*LinuxResidentTrayTest' :verifyApplicationArchitecture`. Extend Task 7 isolated commands with `--gate popup`, each panel, 1x and JBR2x. Harness opens no Jasper workspace; record `Window.getWindows` counts before/after and actual popup type/focus. Use `xdotool` to select all four rows, Escape/outside-click/focus-loss, Tab/Shift-Tab/menu keys, panel loss while open and close while an EDT show is queued. Assert exactly one callback per chosen row, zero after invalidation, no taskbar entry, live/focus-owned resources and owned listeners return to baseline; at most one disposed raw inventory entry may remain after bounded GC and no stuck JVM. A vetoed Quit records the callback but the adapter still discovers/reopens. Capture placement at each edge; virtual multi-screen origins supplement, never certify, physical mixed-DPI acceptance. If public Swing focus/dismissal cannot pass with no other window, fix/amend before production wiring. Commit `feat: add transient Linux tray popup` with trailer after review.

### Task 9: Production scheduling, Linux routing and lifetime

**Prerequisites:** Reviewed Tasks 6–8 Fedora native gates pass; x86_64/Ubuntu22.04 rows remain CI-pending as authorized. No AWT `SystemTray`/`TrayIcon` fallback is constructed.

**Files:** Create `LinuxTrayRuntime.java` and `LinuxTrayRuntimeTest.java` (composition/lifetime only); modify `LinuxResidentTray.java`, `LinuxResidentTrayTest.java`, `ResidentTray.java`, `ResidentTrayTest.java`, `jasper-app/src/test/java/dev/jasper/app/application/ResidentIndicatorTest.java`; adjust existing bootstrap indicator comment/Javadoc only if inaccurate; required inventory rows and verification. This one extra runtime file keeps deterministic controller state separate from thread/adapter construction.

**Interfaces:** `static Runnable LinuxTrayRuntime.install(ResidentTray.Actions actions)` creates the runtime and returns idempotent nonblocking cleanup even when no host exists. Preserve the reviewed `LinuxResidentTray` constructor exactly, delegating to an overload with trailing `Runnable cancelRetry, Consumer<Boolean> modernPublished` and no-op defaults. `cancelRetry` physically cancels the production future; `modernPublished` is a nonblocking state observation used only to suspend/resume legacy discovery. `ResidentTray` gains trailing `Function<Actions,Runnable> linuxInstaller` constructor overload; old two-argument constructor delegates with null. No application lifecycle decisions move to platform code.

- [x] **Step 1: Add failing routing, timer and cleanup tests.** Linux applies only nonheadless, independent of host presence; `forThisDesktop` headless/native-class-load path touches no libraries/connections. Inject installer and assert once-only install/cleanup and no Windows/macOS regressions. Linux `balloon` never adds a temporary AWT icon; existing `LinuxNotifications` remains owner. Runtime tests inject recording factories/executors through a package-private constructor with concrete function parameters, not interfaces; prove cold construction does no native work, install starts discovery only from the settled indicator hook, one daemon controller scheduler, physical timer queue empties on success/restored/close, five-second probe suspended only while modern publication is confirmed, resume snapshots before legacy install, close from EDT returns promptly while blocked native cleanup runs elsewhere.

```java
@Test void linuxEligibilityDoesNotRequireAHost() {
    assertThat(ResidentTray.applies("Linux", false)).isTrue();
    assertThat(ResidentTray.applies("Linux", true)).isFalse();
}
```

Add controller tests `successPhysicallyCancelsRetry`, `connectionRestoredPhysicallyCancelsReconnect`, `closePhysicallyCancelsRetry`, `modernPublicationSuspendsLegacyProbeAfterConfirmedAdd`, `failedWithdrawalCannotResumeLegacyPublication`. Existing controller tests keep old constructor/no-op hooks and exact ordering. Extend `ResidentIndicatorTest` using existing settled/EDT helpers: no-icon runtime still reaches `StartupTrace.Milestone.WARMED_UP`; Quit runs cleanup exactly once. Add integration trace for legacy → modern (confirmed legacy destroy precedes modern publish), modern failure → confirmed withdrawal → legacy, close during each queued callback, stale popup action, and both adapter cleanup failures (try/finally closes the other adapter).

- [x] **Step 2: Run** `./gradlew :jasper-app:test --tests '*ResidentTrayTest' --tests '*LinuxTrayRuntimeTest' --tests '*LinuxResidentTrayTest' --tests '*ResidentIndicatorTest'`; expect routing/hook failures before implementation.
- [x] **Step 3: Compose the exact reviewed adapters and physical cancellation.** Runtime captures launch environment, builds popup, legacy and modern with callbacks to a controller reference set before starting either adapter; callbacks queued earlier are retained/coalesced until reference initialization, not dropped. Use one `ScheduledThreadPoolExecutor(1, Thread.ofPlatform().daemon().name("jasper-tray-controller").factory())`, `setRemoveOnCancelPolicy(true)`. The controller still serializes reconciliation; adapter native work and D-Bus work stay on their own workers. EDT never waits for them.

```java
var modernBinding = new LinuxResidentTray.Modern(
    modern::publish, modern::withdrawAndAwait, modern::close, modern::start);
var legacyBinding = new LinuxResidentTray.Legacy(
    legacy::install, legacy::removeAndAwait, legacy::close);
```

Wire `LinuxStatusNotifier(capturedBusAddress, controller::hostChanged,
connected -> { if (connected) controller.connectionRestored(); else controller.connectionLost(); })` with actual callback construction through the initialized reference. Native availability calls `controller.legacyChanged`. Popup receives adapter MenuRequest; its callback already calls the controller's captured generation dispatch. Dispatch through `SwingUtilities::invokeLater`.

Store exactly one retry `ScheduledFuture<?>`; scheduling cancels the prior future before replacing it. New controller hook `cancelRetry()` clears logical ticket/purpose as today and invokes runtime cancellation **only when a ticket existed**. Production callback `getAndSet(null)` then `cancel(false)`; remove-on-cancel clears scheduler queue. Timer firing clears its own future before enqueuing controller work. No EDT blocking and no cancellation hook recursion. Add `modernPublished.accept(true)` only after successful current-generation publish; emit false only after confirmed modern withdrawal/connection termination, not merely an owner observation or timed-out cleanup. Hook false calls `legacy.discoveryEnabled(true)` and obtains fresh snapshot; true cancels runtime's sole five-second probe and `legacy.discoveryEnabled(false)`. The existing controller fallback/generation/cleanup rules remain intact. No new public controller state accessor.

Runtime starts a single five-second fixed-delay legacy snapshot timer while enabled; expose `void LinuxLegacyTray.probe()` to coalesce a fresh owner snapshot on its native command slot. Native passive event polling remains active while disabled without periodic probing. Avoid two separate five-second timers: Task 7's test driver uses explicit `probe`, and production owns this sole timer. Native close invalidates popup before asynchronous resource drain. Runtime close atomically invalidates controller/adapters immediately, cancels retry/probe/deadlines, queues cleanup, and shuts down controller scheduler after its cleanup task; do not shut down an adapter deadline scheduler before its active operation has returned. Adapter workers stop themselves after releasing resources.

`ResidentTray.forThisDesktop`: retain headless no-op first; on nonheadless Linux inject `LinuxTrayRuntime::install`, preserving Windows/macOS existing path. `install(Actions)` validates, delegates Linux branch before AWT icon logic, and returns an idempotent wrapper even when native unavailable. Linux facade marks notification routing so `balloon` returns without calling `add`; do not change Windows balloon ownership/click tests. Constructor/native loading remains cheap; existing application settled-startup hook triggers install.

- [x] **Step 4: Run focused integration regressions.** `./gradlew :jasper-app:test --tests '*Linux*Tray*Test' --tests '*LinuxStatusNotifierTest' --tests '*ResidentTrayTest' --tests '*ResidentIndicatorTest' --tests '*JasperApplicationResidencyTest' --tests '*JasperApplicationBackgroundTest' --tests '*ApplicationBootstrapTest' --tests '*ThemeMigrationCoverageTest' :verifyApplicationArchitecture`. Repeat Linux required-native/private-bus/controller tests on authorized Fedora, zero required skips. Run transport/legacy/popup probes once against composed runtime on isolated buses/displays; no real Jasper workspace, login registration or active Plasma. Record successful modern preference and one-publication ordering via fixture host. Commit `feat: integrate recoverable Linux resident trays` with trailer after review.

### Task 10: Required Linux CI, packages, guides and final acceptance

**Files:** Modify `.github/workflows/linux-packages.yml`, `docs/configuration.md`, `docs/workspace.md`, `docs/app-maintenance.md`, `docs/packaging.md`, `docs/STATUS.md`, this plan and verification. Modify `jasper-app/build.gradle.kts` only for packaged probe classpath support; SDK is unchanged.

**Interfaces:** Recorded release matrix with exact runtime/library/host versions; CI uses existing native architecture runners (Ubuntu22.04 x64/aarch64). No new supported API. Incomplete user desktop/physical monitor rows stay pending; local virtual success is not a distro compatibility claim.

- [ ] **Step 1: Require the Linux headless and native ABI/transport gates in CI.** Include both platform Java source/test paths, native fixture and runtime/library Gradle changes in workflow push/PR path filters. Install `dbus-daemon libxcb1 libxcb-render0 libxau6 libxcb1-dev libxcb-render0-dev libxau-dev pkg-config` alongside existing build prerequisites. Add required-native tests/probe **before** packaging on both Ubuntu22.04 runners:

```bash
./gradlew :jasper-app:test --tests '*LinuxTrayTransportTest' --tests '*LinuxTrayWireTest' --tests '*LinuxStatusNotifierTest'
./gradlew :jasper-app:linuxTrayNativeTest --tests '*LinuxTrayXcbTest' --tests '*LinuxLegacyTrayTest'
./gradlew :jasper-app:linuxX11TrayProbe --args='--gate transport --require-native --output build/tray-gate/transport'
```

No DISPLAY/desktop bus is required for those gates; protocol fixture supplies owned local sockets. CI missing prerequisites/skipped required rows fail. Preserve existing install/coexistence/package baseline jobs. No GTK/Qt SDK is introduced by tray work; existing unrelated desktop-color SDK jobs stay as they are. Upload ABI/gate logs on failure. x86_64 and Ubuntu22.04 results are CI-only until the workflow actually runs; never prefill them from Fedora ARM64.

- [ ] **Step 2: Run final checks and actual packaged-runtime gates.** `./gradlew check :jasper-app:packageApp :jasper-app:verifyPackage -Pjasper.aot=false` using configured JBR25 and supported Python3.11–3.13; run `git diff --check`, source hygiene scanner from AGENTS, inspect XML counts/skips. No SDK change means no external-plugin rerun/reference regeneration unless actual inputs changed. No AOT, GUI or benchmark task is implied by headless packaging.

Provide `linuxX11TrayProbe -Pjasper.tray.packagedHome=<absolute app-image>` using a Gradle `JavaExec.javaLauncher`/`executable` override to the packaged `lib/runtime/bin/java` and classpath of exact packaged `lib/app/*.jar` plus **test output only** (not main runtimeClasspath dependency jars). Include no unshipped main jar/dependency, so missing package libraries fail. Run transport gate and existing packaged D-Bus address smoke on Fedora aarch64 and both Ubuntu22.04 CI architectures. Repeat isolated real-panel/popup gate with packaged JBR on authorized Fedora. Verify `readelf`/`ldd` for system XCB/Xau sonames, no new shipped native library, one D-Bus transport/NOP provider, no missing notices. All exact commands/artifact hashes/library versions go in verification. Any native cancellation failure or fatal adapter error is a release blocker.

- [ ] **Step 3: Make minimal guide corrections to implemented behavior.** Replace stale Linux-no-tray claims with existing guide prose matching this copy; preserve headings/settings notification anchors:

```markdown
On Linux, Jasper shows a tray icon when the desktop provides a compatible status notifier or local X11 tray.
Its menu provides **Open Jasper**, **New Window**, **Settings…** and **Quit Jasper**. Jasper keeps running without
an icon when no tray is available; launch it normally to reopen it and use Quit in the app to exit. GNOME needs
an enabled indicator extension to show the icon.
```

Describe the owned XCB/local Unix display and regular nonsymlink bounded authority scope in existing maintenance text, and system `libxcb.so.1`, `libxcb-render.so.0`, `libXau.so.6` runtime resolution/worker cleanup beside current platform notes. Do not promise unsupported remote TCP/SSH tray fallback. Document confirmed protocol/tested matrix only; link GNOME extension from existing packaging acceptance notes, never install it automatically.

- [ ] **Step 4: Record user desktop acceptance, preserving pending rows.** Use actual distro point version, architecture, session type, panel/extension version, JBR build and monitor transforms. User-authorized isolated LXDE/XFCE/Xvfb results can fill only their executed rows; do not launch real Jasper or enter the active Plasma session from this task.

| Release family | Cases | Required evidence |
|---|---|---|
| Ubuntu22.04 x86_64/aarch64 | CI headless packaged baseline | ABI/cancellation/private bus both socket forms, install dependencies |
| Ubuntu24.04 LTS | GNOME Wayland/available X11, indicator on/off | late host/no-host residency/focus/reopen/logout |
| Debian12/13 | LXDE/MATE/XFCE X11 | real panel loss/replacement, opaque/alpha composition, focus/menu |
| Fedora44 | authorized isolated LXDE/XFCE aarch64; user GNOME/Plasma Wayland/available X11 | gate evidence separate from real session/modern-host acceptance |
| openSUSE Leap16/current Tumbleweed | Plasma Wayland/X11 where supplied | RPM interoperability, owner/panel replacement |
| Arch dated snapshot | LXQt/XFCE and available Wayland/X11 | modern hosts and legacy replacement |
| Physical monitors | mixed1x/2x and fractional scale, negative origins | popup device identity/edges/insets, no workspace window/taskbar entry |

For each actual desktop row test fresh-login background start without rehearsal/shell, normal launch same PID, last-window close remains resident, all four actions once, vetoed Quit remains live, accepted Quit ends native/menu workers/subscriptions without restart, opt-out persists across upgrade/login, panel/app applet restart and host enable/disable recover one icon, modern failed registration falls back, bus absent/lost recovers, no host remains resident, sleep/wake per modern/legacy family, logout ends old PID/new login gets new owner. A dead desktop leaving an orphan blocks session-lifetime acceptance and requires amendment; a bus disconnect alone remains reconnect. Physical mixed-monitor and compositor cases remain pending until observed, not replaced by virtual screenshots.

- [ ] **Step 5: Final whole-branch review and commit.** Update STATUS/plan with actual completed gates, evidence, deviations and pending CI/user rows; request final independent whole-branch review under the existing workflow, fix findings and rerun affected checks. Commit `docs: record Linux tray compatibility and verification` with `Co-Authored-By: Codex <noreply@openai.com>`. Native source/fix commits in this task follow the same trailer rule. No merge/push without authorization. Benchmarks require separate authorization and AGENTS game/VM exclusion.

## Revised-plan coverage and self-review

Approved amendment coverage: local Unix authority/owned XCB/ABI/bounded setup-output-cancellation → Task 6; host epochs/false-true admissions/XEmbed/confirmed cleanup/render/input/panel gate → Task 7; Swing lifetime/keyboard/coordinate identity → Task 8; reviewed controller contracts/physical timers/settled routing/no-icon residency/EDT guards → Task 9; packaged runtime/CI release split/guides/desktop and session acceptance → Task 10. Completed Tasks 1–5 retain their original evidence, with Task 2's public-AWT approach permanently superseded after FAILED characterization.

Every native proof records executed prerequisites and versions. Authority acquisition retains the approved cooperative filesystem limit; held-fixture release proves eventual cleanup, not syscall interruption. Missing library/host, skipped required tests, child timeout, stranded native worker, ambiguous monitor identity or unconfirmed teardown is unavailable/failed/pending as appropriate, never a passing compatibility claim. Gate fixes stay inside the approved architecture; a failed cancellation or Swing mechanism that needs another architecture requires an amendment before wiring. Existing isolated probe authorization and execution workflow carry forward; this revision does not request another plan-approval round.

Controller Rulings 28–30 and the scoped native-oracle adjustments are recorded in the
[approved popup correction](../specs/2026-10-28-linux-tray-popup-correction-proposal.md) and verification.
The final implementation removes failed USER_TIME property variants and permits one genuine source1
WM-mediated activation request, with leased peer, bounded focus denial and acknowledged retirement.
Popup-mode taskbar/pager comparisons use the embedded-panel baseline; Task7 artwork validation stays in
legacy mode. The raw inventory oracle permits at most one disposed entry while requiring zero live/focus
ownership and exact owned-listener baseline.

Controller Ruling 32: native adapter event polling uses separate idle-wait and operation absolute deadlines.
The 100 ms idle budget may end normally; potentially flushing event drains retain the current operation's
1.75 s bound, protected duplicate shutdown and cancellation. Ordinary-load integration exposed the earlier
verbose-only observation; a real-boundary 150 ms delay reproduces erroneous host loss before this correction.
The one-argument transport API retains its previous deadline semantics. Cost: one concrete overload, one adapter
call-site adjustment and a native regression; this is a runtime-discovered scope addition, not a relaxed watchdog.
