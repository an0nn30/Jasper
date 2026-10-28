# Linux residency 2: modern and legacy trays Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a recoverable Linux resident icon through Java D-Bus StatusNotifierItem/DBusMenu, falling back to X11 while retaining residency with no icon.

**Architecture:** An application-owned `LinuxResidentTray` coordinates two concrete adapters behind `ResidentTray.install`. A small pure policy selects the preferred capability; a generation guards all asynchronous publications and actions. D-Bus owns its connection and exports; AWT owns the legacy icon on the EDT.

**Tech Stack:** JBR 25, dbus-java core plus one Unix transport, public AWT, JUnit 5/AssertJ, isolated `dbus-daemon`, Gradle wrapper.

**Spec:** [Approved Linux residency design](../specs/2026-10-27-linux-residency-design.md).

**Status (2026-10-27):** Task 1 passed the user-directed Fedora 44 aarch64 gate: 49 focused tests and both packaged-runtime address cases, notices/architecture and headless packaging. [Verification](2026-10-27-linux-residency-2-verification.md) records exact versions and evidence. Rulings 9–10 follow the user's split: x86_64 and Ubuntu 22.04 remain CI-pending; after Task 1 review, Tasks 4–5 may proceed with the pinned 5.2.0 bindings below. Task 6 retains its separate AWT gate. Rulings 1–2 allow local preparation and replace the fixture sketch with short `/tmp` paths and bounded reader/process cleanup. Ruling 4 selects junixsocket after the released JDK transport's abstract-address rejection; Ruling 5 aligns application SLF4J API/nop to 2.0.17. Ruling 7 replaces the non-JAR aggregator with its exact common/native-common 2.10.1 jars. Ruling 8 excludes the confirmed compile-only annotation leak after packaging reproduced its module failure, superseding Ruling 6's temporary EPL investigation. Costs: native artifacts, explicit dependency tracking and a logging-version regression surface. The CI baseline and desktop matrix are not certified. Task 2 preparation is compiled locally and on Fedora ARM64; native legacy recovery remains pending. Task 3's shared menu, ARGB conversion and capability/retry policy pass focused tests and architecture/inventory checks. Deviation: add Task 3's four new Java sources to the theme source inventory because its source discovery scans AWT imports, including headless tests. Task 4 implements the typed wire exports and bounded owner-scoped adapter; 19 Fedora private-bus tests pass, including independent exact `gdbus` introspection for both address forms. Controller Ruling 12 requires generic Tuple carriers and explicit Variant returns from `Properties.Get`; Ruling 13 narrowly repairs the pinned library's duplicated tuple-output introspection through public metadata APIs. Both accommodations are version-specific and must be revalidated on upgrade. Task 4 review fix 1 drains cleanup requests and releases worker ownership in one locked handoff, with a package-private no-op Runnable seam for deterministic tests; publication retains the discovery endpoint and verifies the daemon GetId before registration. Two required source-inventory rows were added. Earlier deviations: add the native test probe to the theme source inventory and correct Task 1 evidence wording (ARM64 passed; existing cold Linux compiler warnings retained).

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
4. A legacy panel restarts, or GNOME enables its extension while the fallback is shown — Tasks 2 and 5 recovery/single-publication gates.
5. Cold background login races with a foreground launch or logout — Task 7 native session matrix plus existing bootstrap tests; a tray disappearance is never interpreted as logout.

## File map

All new production Java below is under `jasper-app/src/main/java/dev/jasper/app/platform/`; same-package tests live under `jasper-app/src/test/java/dev/jasper/app/platform/`.

| File | Responsibility |
|---|---|
| `LinuxTrayPolicy.java` | Pure target selection and capped retry delay |
| `LinuxTrayMenu.java` | Stable IDs, labels and action dispatch shared by both adapters |
| `LinuxTrayDbus.java` | Wire interfaces and value structs, with exact D-Bus signatures |
| `LinuxStatusNotifier.java` | Session connection, watcher discovery, exports and modern registration |
| `LinuxLegacyTray.java` | Public AWT installation/removal and capability observations |
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

**Files:** Create test-only `LinuxTrayProbe.java`; modify verification report; no production fallback until the gate passes.

**Interfaces:** Explicit opt-in test-class `main(String[] args)`; no Gradle `check` dependency. It runs for at most three minutes and never launches Jasper or a shell.

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
    }
}
```

- [x] **Step 2: Compile headlessly** with `./gradlew :jasper-app:compileTestJava`. Compilation passing proves only that the public API is usable.
- [ ] **Step 3: Hand the probe to the user for an authorized session run** with the bundled JBR, `-Djava.awt.headless=false`, application/test classpaths, and main `dev.jasper.app.platform.LinuxTrayProbe`. Run LXDE on X11 and a second legacy tray configuration (MATE/XFCE legacy host). Start with no host, enable it, restart the panel, remove/re-add the tray applet, click menu/activation, inspect alpha and 1×/2× scale. Record whether the original icon returns after panel restart and whether Java notices host loss. The probe's `current` reference is intentionally retained to characterize automatic JBR recovery. A visible ghost or lost icon is a failure even if `getTrayIcons()` still lists it.
- [ ] **Step 4: Record PASS/failure evidence**, including whether public support/property signals suffice or a periodic remove/re-add is necessary. If the public API supports recovery, proceed with the adapter below. If it does not, stop Task 6 and amend the design for an X11/JNA fallback; do not import `sun.awt` or use an endless icon-reinstall timer as an unreviewed workaround. Independent protocol work may proceed after Task 1 while this gate awaits the user.
- [ ] **Step 5: Commit** `test: characterize Linux legacy tray recovery` with trailer. Task completion requires recorded native evidence; committing the fixture alone leaves the gate pending.

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
              Runnable withdraw, Runnable close) { }
record Legacy(java.util.function.Predicate<java.util.function.IntConsumer> install, Runnable remove, Runnable close) { }
```

Controller events: `hostChanged(Host host)` (null = no live host), `legacyChanged(boolean supported)`, `connectionLost()`, `close()`. All events are serialized. Test construction is `LinuxResidentTray(ResidentTray.Actions actions, Modern modern, Legacy legacy, Consumer<Runnable> enqueue, BiConsumer<Duration,Runnable> retry, Consumer<Runnable> edt)`; production uses bounded executors and scheduled callbacks. Model observations/pending work separately from published state.

- [ ] **Step 1: Write deterministic event tests.** Use recording functions and a manually drained `ArrayDeque<Runnable>` for each queue. The interface tests below define the complete assertions for the critical transition path; `events` is the adapters' shared recording list:

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

- [ ] **Step 2: Run** `./gradlew :jasper-app:test --tests '*LinuxResidentTrayTest'`; expect missing controller or failed ordering.
- [ ] **Step 3: Implement the serialized state machine.** Use the following exact transition algorithm; it is intentionally independent of dbus-java:

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

- [ ] **Step 4: Run** `./gradlew :jasper-app:test --tests '*LinuxResidentTrayTest' --tests '*LinuxStatusNotifierTest' --tests '*LinuxTrayPolicyTest'`. Expect zero failures. Use timeouts/latches, not sleeps, for controlled races.
- [ ] **Step 5: Commit** `feat: recover Linux tray hosts without duplicate icons` with trailer.

### Task 6: AWT fallback and application wiring

**Files:** Create `LinuxLegacyTray.java`; modify `ResidentTray.java` and `ResidentTrayTest.java`; add same-package `LinuxLegacyTrayTest.java`; extend `jasper-app/src/test/java/dev/jasper/app/application/ResidentIndicatorTest.java` only where lifecycle assertions are missing.

**Interfaces:** `LinuxLegacyTray.install(IntConsumer action)` returns a nullable removal callback on EDT; `supported()` runs on EDT; no desktop work in static initialization. Controller wraps callbacks with the current generation. `ResidentTray` gains a Linux installer function while retaining its existing macOS/Windows constructor/test seam.

- [ ] **Step 1: Add the routing tests.** Replace the Linux-not-supported expectation with tests that Linux is eligible only when not headless and that its injected installer is invoked exactly once, its cleanup is forwarded once, and Windows/macOS continue through their existing icon path. Headless `forThisDesktop()` must neither connect nor initialize a native tray. Keep all balloon ownership/click regression tests.

```java
@Test void linuxEligibilityDoesNotRequireAHost() {
    assertThat(ResidentTray.applies("Linux", false)).isTrue();
    assertThat(ResidentTray.applies("Linux", true)).isFalse();
}
```

Use injected `Function<Actions,Runnable>` to assert no actual desktop API is touched. Add a no-icon installer lifecycle test that marks `StartupTrace.Milestone.WARMED_UP`, then Quits, and asserts its cleanup runs exactly once despite never showing an icon; use `ResidentIndicatorTest`'s existing `application`, `resident`, `settled` and EDT helpers.

- [ ] **Step 2: Run** `./gradlew :jasper-app:test --tests '*ResidentTrayTest' --tests '*ResidentIndicatorTest' --tests '*LinuxLegacyTrayTest'`; expect Linux routing failure. **Task 2 native gate must already pass before committing the production fallback.**
- [ ] **Step 3: Add the complete AWT installation method** in `LinuxLegacyTray` (imports JDK AWT, `AtomicBoolean`, `IntConsumer`):

```java
static Runnable install(IntConsumer action) {
    if (!javax.swing.SwingUtilities.isEventDispatchThread())
        throw new IllegalStateException("Legacy tray installation requires EDT");
    if (!supported()) return null;
    try {
        SystemTray tray = SystemTray.getSystemTray();
        PopupMenu menu = new PopupMenu();
        for (LinuxTrayMenu.Row row : LinuxTrayMenu.rows()) {
            if (row.separator()) { menu.addSeparator(); continue; }
            MenuItem item = new MenuItem(row.label());
            item.addActionListener(event -> action.accept(row.id()));
            menu.add(item);
        }
        TrayIcon icon = new TrayIcon(ResidentTray.image(ApplicationIcon.images("linux"), tray.getTrayIconSize()),
            "Jasper is running in the background", menu);
        icon.setImageAutoSize(true);
        icon.addActionListener(event -> action.accept(1));
        tray.add(icon);
        AtomicBoolean removed = new AtomicBoolean();
        return () -> { if (removed.compareAndSet(false, true)) tray.remove(icon); };
    } catch (AWTException | RuntimeException failure) {
        return null;
    }
}

static boolean supported() {
    return !GraphicsEnvironment.isHeadless() && SystemTray.isSupported();
}
```

Surround `supported()` exceptions in the controller's capability probe; log installation errors through the
controller's state-based diagnostics, not on every probe. Apply the Task 2-proven public support/property recovery
behavior. Removal remains EDT-only; never call it from the D-Bus thread.

In `ResidentTray`, add a nullable final `Function<Actions,Runnable> linuxInstaller` and a constructor overload to
inject it; have the existing `(boolean mac, BiFunction<Runnable,Optional<Actions>,Icon> add)` constructor delegate
with null. `forThisDesktop()` first preserves the headless no-op path, then for Linux constructs the facade with
`LinuxResidentTray::install`; add that production composition method now, creating the Task 5 controller and owning
Task 4 discovery and the AWT adapter, then starting discovery/probing. At the top of `install(Actions)` after null validation, delegate to that function if
present; wrap its returned cleanup in an idempotent callback. Other paths, including Windows balloon-only temporary
icons, remain unchanged. Linux system notifications continue to use `LinuxNotifications`; `balloon` must not
accidentally create a second legacy icon. Add an explicit Linux early return there if needed. Update only relevant
Javadocs and bootstrap's indicator comment; do not move existing lifecycle decisions into the platform package.

- [ ] **Step 4: Run** `./gradlew :jasper-app:test --tests '*ResidentTrayTest' --tests '*ResidentIndicatorTest' --tests '*Linux*Tray*Test' --tests '*JasperApplicationResidencyTest' --tests '*JasperApplicationBackgroundTest' --tests '*ApplicationBootstrapTest'` and `./gradlew verifyApplicationArchitecture`. Expect zero failures; Linux wire tests require Linux/private bus, and are not counted as verified when skipped on macOS.
- [ ] **Step 5: Commit** `feat: integrate Linux resident trays with X11 fallback` with trailer.

### Task 7: Package, document and accept the desktop matrix

**Files:** Modify `.github/workflows/linux-packages.yml`, `docs/configuration.md`, `docs/workspace.md`, `docs/app-maintenance.md`, `docs/packaging.md`, `docs/STATUS.md`, this plan and its verification report. Update `jasper-app/build.gradle.kts` only if a dedicated packaged smoke classpath/task is needed.

**Interfaces:** Release gate is a recorded matrix with actual platform/runtime versions. No new supported API.

- [ ] **Step 1: Require private-bus tests in Linux CI.** Add `dbus-daemon` to the Linux package job's prerequisite packages. Run `./gradlew :jasper-app:test --tests '*LinuxTrayTransportTest' --tests '*LinuxTrayWireTest' --tests '*LinuxStatusNotifierTest'` on both architecture runners. Expand the workflow path filters to include `jasper-app/src/main/java/dev/jasper/app/platform/**` and the corresponding tests so tray changes trigger this gate. Tests must fail on missing private-bus prerequisites in this job; macOS/Windows may skip Linux-only cases. Never use the host desktop bus. Retain existing package install/coexistence tests.
- [ ] **Step 2: Run final automated checks**: `./gradlew check :jasper-app:packageApp :jasper-app:verifyPackage -Pjasper.aot=false`, then packaged-runtime transport/wire smoke on Linux x86_64/aarch64. Confirm every runtime artifact has a third-party notice, service providers are present, and the package still installs on its existing minimal baseline. Review XML counts and skipped tests; run `git diff --check`. No SDK changes expected; regenerate `sdkReference` only if its inputs changed.
- [ ] **Step 3: Update the existing guides with exact behavior copy**, replacing stale Linux-no-icon claims:

```markdown
On Linux, Jasper shows a tray icon when the desktop provides a compatible status notifier or X11 tray. Its menu
provides **Open Jasper**, **New Window**, **Settings…** and **Quit Jasper**. Jasper keeps running without an icon when
no tray is available; launch it normally to reopen it and use Quit in the app to exit. GNOME needs an enabled
indicator extension to show the icon.
```

Add the platform owner/worker/cleanup description beside existing residency maintenance text. Preserve notifications
and settings anchors. Link the GNOME extension in the existing packaging acceptance notes, not an automatic install
command. Documentation promises protocol support and the tested matrix, not every combination of distro/DE version.

- [ ] **Step 4: Hand native acceptance to the user.** Test the following release families; record exact installed point
versions, session type, host/extension version, JBR build and architecture. Unavailable rows remain pending.

| Baseline | Desktop/session cases | Required observations |
|---|---|---|
| Ubuntu 22.04, x86_64/aarch64 | Headless packaged runtime baseline | Both socket forms, native ABI/provider, installation |
| Ubuntu 24.04 LTS | GNOME Wayland and available X11; extension on/off | Late host, no-host residency, focus/reopen, logout |
| Debian 12/13 | LXDE X11, MATE X11, XFCE X11 | Legacy recovery and scaling; modern preference where present |
| Fedora 44 | GNOME Wayland; Plasma Wayland and available X11 | Modern menu/pixmaps, extension changes, session logout |
| openSUSE Leap 16 or current Tumbleweed | Plasma Wayland/X11 where supplied | RPM-family interoperability, panel restart |
| Arch snapshot dated at execution | LXQt and XFCE; available Wayland/X11 | Modern hosts, panel replacement, same protocol code |

Every GUI row runs fresh-login background start with no visible rehearsal/shell; normal launch reaches the same PID;
last-window close keeps the PID; app/tray Quit honors close guards and exits without auto-restart; explicit off stays
off across upgrade/login; panel restart and host enable/disable recover one icon; menu and direct activation work
under compositor focus policy; logout terminates the old PID and new login gets a new owner. Test sleep/wake once
per modern/legacy family. If session teardown leaves Jasper alive with a dead display, mark a release blocker and
amend the design for session lifetime integration. A bus disconnect alone must continue to mean reconnect.

- [ ] **Step 5: Record results and commit** `docs: document Linux tray compatibility and verification` with trailer.
Request final whole-branch review, fix findings and rerun affected tests. Record unexecuted GUI/performance cases as
pending, not passed. Warm/cold latency and idle resource measurements require separately authorized benchmarks and
must be skipped while a game/VM is running per AGENTS.md. Do not merge or push without user authorization.

## Conditional-plan binding and self-review

This plan is deliberately gated where the approved spec required evidence before detailed implementation. Task 1
must freeze the dependency and all exact library bindings; Task 2 must establish the actual AWT recovery path.
The protocol table, controller transitions, behavior tests and application contracts above remain binding. Replace
conditional library/fixture bindings with the gate-verified code in the task's plan update before starting dependent
work; record any deviation in this banner and STATUS. A failed gate is not permission to omit the fallback or weaken
wire/session tests.

Coverage: tray menu and icon data → Task 3; protocols, discovery, owner races and bounded calls → Task 4; recovery,
single publication and threading → Task 5; fallback and facade → Tasks 2/6; packaging, GNOME/no-tray behavior, distro
coverage and session lifecycle → Tasks 1/7. Existing application lifecycle and login registration come from Plan 1.
No current product code or dependency has been changed merely by writing these plans.

Research inputs: [publisher release](https://github.com/hypfvieh/dbus-java/releases/tag/dbus-java-5.2.0),
[pinned builder API](https://github.com/hypfvieh/dbus-java/blob/dbus-java-parent-5.2.0/dbus-java-core/src/main/java/org/freedesktop/dbus/connections/impl/DBusConnectionBuilder.java),
[pinned Properties API](https://github.com/hypfvieh/dbus-java/blob/dbus-java-parent-5.2.0/dbus-java-core/src/main/java/org/freedesktop/dbus/interfaces/Properties.java),
and the approved spec's interoperability references. These are planning evidence, not executed acceptance.
