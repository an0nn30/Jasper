# Linux residency Plan 2 verification — 2026-10-27

**Task 1 Fedora aarch64 gate: PASS; per-task review approved.** Rulings 9–10 follow the user's
architecture split: execute aarch64 on the authorized host and defer x86_64/Ubuntu 22.04 baseline
to CI. Those CI rows are **PENDING**, not passed; Tasks 4–5 may proceed after Task 1 review under
this explicit split. Task 6 still requires the separate AWT gate. The controller's earlier SSH
attempts timed out; the replacement host `dustin@10.211.55.13` worked. No installed Jasper was changed.

## Candidate and published evidence

The candidate is `com.github.hypfvieh:dbus-java-core:5.2.0` and exactly one transport,
`com.github.hypfvieh:dbus-java-transport-junixsocket:5.2.0`. This pin passed Fedora aarch64 private-bus and packaged-runtime checks;
x86_64 and Ubuntu 22.04 acceptance remain pending in CI. [Publisher release](https://github.com/hypfvieh/dbus-java/releases/tag/dbus-java-parent-5.2.0)
was published 2025-12-21; the publisher's latest-release API still identifies it as latest on 2026-10-27.
[Maven Central release POM](https://repo.maven.apache.org/maven2/com/github/hypfvieh/dbus-java-parent/5.2.0/dbus-java-parent-5.2.0.pom)
requires Java 17 and SLF4J 2.0.17, and names junixsocket 2.10.1.
The released JDK transport's `NativeUnixSocketTransport` requires `path` and explicitly documents
that abstract sockets are unsupported. Its exact Maven Central source artifact was inspected; this
source evidence chooses a candidate but substitutes for no required Linux execution.

Junixsocket remains maintained: [2.11.0](https://github.com/kohlschutter/junixsocket/releases/tag/junixsocket-2.11.0)
was published 2026-08-10. This preparation retains the exact 2.10.1 dependency declared by dbus-java 5.2.0.
The newer release includes connect/EOF/virtual-thread corrections; the Fedora aarch64 gate below passed with 2.10.1, and the remaining CI
matrix must still assess that pin rather than silently overriding it. The publishers' GitHub public
security-advisory APIs returned empty lists for both repositories on 2026-10-27; this is not a complete
vulnerability audit or proof of security.

Final application runtime additions:

| Coordinate | License / distribution evidence |
|---|---|
| `com.github.hypfvieh:dbus-java-core:5.2.0` | MIT; exact release LICENSE copied |
| `com.github.hypfvieh:dbus-java-transport-junixsocket:5.2.0` | Same MIT project |
| `com.kohlschutter.junixsocket:junixsocket-common:2.10.1` | Apache-2.0; upstream LICENSE/NOTICE inspected |
| `com.kohlschutter.junixsocket:junixsocket-native-common:2.10.1` | Apache-2.0; native C sources and JNI headers inspected; NOTICE retained |
| `org.slf4j:slf4j-api:2.0.17` | Existing MIT component, updated application version |
| `org.slf4j:slf4j-nop:2.0.17` | Existing MIT component, aligned with API |

Bundled plugins keep their existing private SLF4J API/provider 2.0.13 jars. They remain in the manifest;
none is on the application classpath. The final notices check covers **37 shipped modules**.

The transport's published POM declares junixsocket-core as `type=pom`; Gradle includes that POM
as a runtime file. `verifyApplicationArchitecture` then failed with jdeps `Bad magic number`.
The build excludes the aggregator and explicitly declares its exact common/native-common 2.10.1 jars.
The transport also leaks compiler-annotations 1.7.4 into runtime. Its annotation declarations have
CLASS retention, the transport source/module descriptor never references it, and junixsocket's module
uses static requirements and its parent marks annotations provided. Keeping that runtime leak made
`verifyPackage` fail: `Module org.eclipse.jdt.annotation not found, required by com.kohlschutter.annotations.compiletime`.
The build excludes that edge. Compiler annotations, JDT annotations 2.3.0 and Error Prone annotations
2.30.0 consequently are absent from the final shipped runtime. No packaging or architecture check was weakened.

## Local evidence

Host: macOS arm64; configured JBR `25.0.4.1+1-b583.48` (actual discovered installation,
`~/Library/Java/JavaVirtualMachines/jbrsdk-25.0.4.1-osx-aarch64-b583.48`). No GUI, benchmark,
AOT training or desktop session bus was started. Full `check` is reserved for final integration.

RED: `./gradlew :jasper-app:compileTestJava` failed before dependency additions with
`package org.freedesktop.dbus.connections.impl does not exist` and unresolved `DBusConnectionBuilder`.
A temporary EPL-2.0 display regression also failed with `Unknown license EPL-2.0 for JDT Annotations`;
that parser extension/test was removed once the confirmed compile-only runtime leak was excluded.

Final command (after both dependency metadata corrections):

```sh
./gradlew :jasper-app:dependencies --configuration runtimeClasspath \
  :jasper-app:test --tests '*LinuxTrayTransportTest' --tests '*ThirdPartyNoticesTest' \
  --tests '*AppLogTest' --tests '*ResidentTrayTest' --tests '*ResidentIndicatorTest' \
  verifyThirdPartyNotices verifyApplicationArchitecture \
  :jasper-app:installDist :jasper-app:packageApp :jasper-app:verifyPackage -Pjasper.aot=false
```

Passed in 32 seconds. XML: 48 tests, 0 failures/errors, 1 skipped class placeholder (Linux transport);
**47 executed and passed**. No Linux address case executed in this local run. Notices and architecture passed;
package image verified with 41 runtime modules and no AOT cache. The initial dependency compile
reported existing unrelated test fixture warnings; the final run had no new source warnings.
`git diff --check` and source control/private-use/unpaired-surrogate scans of the two new Java files passed.

A separate headless smoke compiled a test-only `PackagedProviders` class and ran it using only
`Jasper.app/Contents/runtime/Contents/Home/bin/java` and `Jasper.app/Contents/app/*.jar` plus that class.
It instantiated the transport service provider, enumerated logging providers, loaded `ResidentTray`,
and obtained the logging factory; no connection or AFSocket initialization was requested. Output:

```text
transport=[dbus-java-transport-junixsocket], logging=[org.slf4j.nop.NOPServiceProvider]
factory=org.slf4j.helpers.NOPLoggerFactory
```

These are macOS packaged service-discovery results. The Linux results are recorded separately below. Windows
headless packaging/startup compatibility remains pending.

## Native resources and pending ABI evidence

The exact native-common jar contains both `.so` and `.nodeps.so` variants at:
`lib/amd64-Linux-clang/jni/libjunixsocket-native-2.10.1[.nodeps].so` and
`lib/aarch64-Linux-clang/jni/libjunixsocket-native-2.10.1[.nodeps].so`.
Static ELF inspection found machine 62/x86_64 and 183/aarch64. Ordinary binaries need only `libc.so.6`;
version strings reach GLIBC_2.14 on x86_64 and GLIBC_2.17 on aarch64. Nodeps variants have no DT_NEEDED
entries or GLIBC labels. These static findings do not prove loading or Ubuntu 22.04 compatibility.

Released `NativeLibraryLoader` extracts to `org.newsclub.net.unix.library.tmpdir`, defaulting to
`java.io.tmpdir`, then calls `System.load`; on load failure it may try user home/current directory.
An extractable, loadable location and JNI native access are required; Jasper already supplies
`--enable-native-access=ALL-UNNAMED`. It attempts the linked binary before nodeps. JNI headers offer
Apache-2.0 among their alternatives; their original attribution is retained in junixsocket.NOTICE.txt.
No GTK/Qt dependency is added. Fedora aarch64 loader/extraction/cleanup and ABI results are below. Both socket forms on
Ubuntu 22.04 x86_64/aarch64 and x86_64 native execution remain pending in CI.

## Executed Fedora aarch64 gate

Source: committed Task 1 archive `766b9c086` (the subsequent evidence amendment changes documentation only),
staged with `git archive` into `/home/dustin/jasper-tray-gate.1yPuL3`. No Git metadata, build outputs,
caches or scratch reports were transferred. No remote Git initialization was needed. Keep this isolated
directory for later private-bus wire tests; `/opt/jasper-workstation` was untouched.

Host: Fedora Linux 44 KDE Plasma Desktop Edition, aarch64, GNU libc 2.43, dbus-daemon 1.16.2;
JBR `25.0.4.1+1-b635.75-jcef` from `/home/dustin/.sdkman/candidates/java/current`.
The build ran with `DISPLAY`, `WAYLAND_DISPLAY` and `DBUS_SESSION_BUS_ADDRESS` removed:

```sh
env -u DISPLAY -u WAYLAND_DISPLAY -u DBUS_SESSION_BUS_ADDRESS \
  JAVA_HOME=/home/dustin/.sdkman/candidates/java/current \
  PATH=/home/dustin/.sdkman/candidates/java/current/bin:/usr/local/bin:/usr/bin:/bin \
  ./gradlew :jasper-app:dependencies --configuration runtimeClasspath \
  :jasper-app:test --tests '*LinuxTrayTransportTest' --tests '*ThirdPartyNoticesTest' \
  --tests '*AppLogTest' --tests '*ResidentTrayTest' --tests '*ResidentIndicatorTest' \
  verifyThirdPartyNotices verifyApplicationArchitecture \
  :jasper-app:installDist :jasper-app:packageApp :jasper-app:verifyPackage -Pjasper.aot=false
```

Passed in 2m 19s, 94 tasks executed. XML: **49 executed/passed, 0 failures/errors/skips**;
transport XML specifically records `abstract=false` and `abstract=true`, both passing.
The cold Linux compile log includes existing unrelated compiler warning noise (`100 warnings`);
this was not pristine output and no warning-free claim is made.
Notices verified 37 shipped modules; architecture passed. Native launcher: 152 checks, 0 failures.
The verified Linux image contains 41 runtime modules, 198 MB runtime and 29 MB default CDS archive;
no AOT cache was trained.

The replay command below then ran using the package's JBR and package's production jars, plus only
JUnit console 6.1.3, AssertJ 3.27.7 and the compiled transport test/fixture. It added
`-Dorg.newsclub.net.unix.library.tmpdir=<fresh /tmp directory>` and
`-Xlog:library=debug:file=runner/native-load.log`, with a 60-second process deadline and all three desktop
environment variables removed. Result: **2 tests started, 2 successful, 0 skipped/failed/aborted** (172 ms).
The native trace records loading an extracted linked `.so` from that directory; both smoke JVMs exited
and the directory was empty and removed successfully. No desktop bus or GUI was used.

A separate test-only `NativeProviders` class, compiled against packaged jars and run with packaged Java,
confirmed exactly one transport and NOP logging provider, `AFSocket.isSupported()` and abstract-namespace
capability, loaded `ResidentTray`, and reported:

```text
transport=[dbus-java-transport-junixsocket], logging=[org.slf4j.nop.NOPServiceProvider]
native=junixsocket-native-common/junixsocket-native-2.10.1
factory=org.slf4j.helpers.NOPLoggerFactory
```

The transport jar's service entries name only `JUnixSocketTransportProvider` and `JUnixSocketSocketProvider`.
Actual `readelf -h -d -V` on aarch64 linked/native nodeps resources confirms AArch64 ELF64. The linked
variant requires `libc.so.6` and only GLIBC_2.17; `ldd` resolves libc to `/lib64/libc.so.6` on this host.
The nodeps variant has no DT_NEEDED/version requirements and `ldd` reports statically linked. `ldd`
warned the extracted inspection files lacked execution permission; actual Java loading and both address
tests passed without changing permissions. This proves Fedora 44 aarch64 loading, not the deferred Ubuntu baseline.

Remote raw evidence: `gate.log`, `packaged-smoke.log`, `runner/native-load.log`, Gradle test XML and
`runner/results/` under the isolated directory. Copies of the three logs are at
`/tmp/jasper-dbus-evidence/linux-aarch64-{gate,packaged-smoke,native-load}.log` on the controller host.

## Verified 5.2.0 library bindings

The exact published source and resolved jars provide `DBusConnectionBuilder.forAddress(String)`,
`withShared(false)`, `build()`, `withDisconnectCallback(IDisconnectCallback)` and
`transportConfig().withTimeout(int).back()`. Connections expose `getRemoteObject(..., Class<T>)`,
`exportObject`, `unExportObject`, `getUniqueName`, `isConnected`, `close` and `addSigHandler`
returning an `AutoCloseable` subscription. `Properties` uses generic `Get`/`Set` and
`Map<String,Variant<?>> GetAll`; `Variant(value, String signature)` accepts explicit signatures.
`UnknownInterface`, `UnknownProperty` and `PropertyReadOnly` are present. These bindings are frozen for
Tasks 4–5; actual protocol exports/signatures remain the Task 4 wire-test deliverable.

**Timeout limitation:** `transportConfig().withTimeout` feeds transport selection/retry logic; it does
not itself bound a blocking junixsocket connection or method call. `MethodCall.setDefaultTimeout(long)`
is process-global. Task 4 must own scoped deadlines, close/cancellation and the promised unresponsive
watcher/connection tests; do not mistake these available APIs for a proven bounded I/O contract.

## Remaining CI and repeatable Linux replay

Repeat in the pending x86_64 and Ubuntu 22.04 CI rows, with JBR 25 and `dbus-daemon`
installed; record `/etc/os-release`, `uname -m`, JBR build, `dbus-daemon --version`, and glibc version.
Do not use the desktop's session bus. Missing `dbus-daemon` must fail this test, not skip it.

```sh
./gradlew :jasper-app:test --tests '*LinuxTrayTransportTest' \
  verifyThirdPartyNotices verifyApplicationArchitecture \
  :jasper-app:installDist :jasper-app:packageApp :jasper-app:verifyPackage -Pjasper.aot=false
```

Require **two executed, passing address cases** and zero skipped cases. The fixture makes directories
directly under `/tmp`, waits at most five seconds for the address, and bounds daemon/reader cleanup.
For packaged execution, first fetch only test-runner artifacts, then use only packaged production jars:

```sh
mkdir -p /tmp/jasper-transport-runner
curl -fLsS https://repo.maven.apache.org/maven2/org/junit/platform/junit-platform-console-standalone/6.1.3/junit-platform-console-standalone-6.1.3.jar \
  -o /tmp/jasper-transport-runner/junit-console.jar
curl -fLsS https://repo.maven.apache.org/maven2/org/assertj/assertj-core/3.27.7/assertj-core-3.27.7.jar \
  -o /tmp/jasper-transport-runner/assertj.jar
jasper-app/build/packaging/image/Jasper/lib/runtime/bin/java \
  --enable-native-access=ALL-UNNAMED -Djava.awt.headless=true \
  -cp 'jasper-app/build/packaging/image/Jasper/lib/app/*:jasper-app/build/classes/java/test:/tmp/jasper-transport-runner/*' \
  org.junit.platform.console.ConsoleLauncher execute \
  --select-class dev.jasper.app.platform.LinuxTrayTransportTest --fail-if-no-tests \
  --details tree --reports-dir /tmp/jasper-transport-runner/results
```

This packaged command passed on Fedora aarch64 with the extra extraction/trace flags described above.
The remaining CI rows are pending. The runtime and every production dependency above
come from the Linux package; the only external jars are JUnit and AssertJ. Inspect the packaged
service entries for `org.freedesktop.dbus.spi.transport.ITransportProvider` and
`org.freedesktop.dbus.spi.message.ISocketProvider`; each must name only the junixsocket implementation.
Run equivalent packaged provider smoke for exactly one NOP logging provider. Extract each architecture's
linked/nodeps resources to `/tmp`, run `readelf -h -d -V` and `ldd` on that host, and record actual native
loading with both socket forms. Repeat on the installed Ubuntu 22.04 package and record installation,
provider and ABI results. Full final integration check remains required after the gated tasks land.

## Controller rulings / deviations

1. Initially prepare on macOS without marking native gates complete; the later user-directed split in Rulings 9–10 opens Tasks 4–5 after aarch64 evidence/review.
2. Use short `/tmp` paths and dedicated bounded reader/process cleanup instead of the common-pool sketch.
4. Prepare junixsocket directly from exact released source's JDK abstract-address rejection; this is not a Linux pass.
5. Align application SLF4J API/nop with released dependency's 2.0.17; plugin providers stay private.
6. Initially authorize EPL-2.0 registry support for leaked annotations; superseded by Ruling 8, so no registry change ships.
7. Exclude non-JAR aggregator, explicitly track its exact runtime jars; future upgrades must revisit those declarations.
8. Exclude confirmed compile-only annotation runtime leak; preserve strict architecture/package checks.
9–10. User-directed split: Fedora aarch64 execution passes on the replacement host; x86_64 and Ubuntu 22.04 remain CI-pending. Tasks 4–5 may proceed after review; Task 6 retains its separate AWT gate.

Task 1 meets the user-directed aarch64 execution gate, with remaining CI acceptance explicit. No product tray behavior was added.

## Task 2 public AWT probe — preparation passed; native gate PENDING

`LinuxTrayProbe` is the plan's exact disposable test-only class, including its retained icon reference,
two-second polling and three-minute exit. It is opt-in, never a `check` dependency. No production
fallback was added. Minor deviations: the theme source inventory includes its `native-platform` row;
Task 1 build/evidence wording was corrected to distinguish ARM64 success from deferred CI and retain
existing cold Linux compiler warning evidence.

Headless local command `./gradlew :jasper-app:compileTestJava :jasper-app:test --tests '*ThemeMigrationCoverageTest'`
passed in 4 seconds: **5 tests, 0 failures/errors/skips**. Log: `/tmp/jasper-task2-local.log`.
Only the probe source was copied into the retained isolated Linux directory; with `DISPLAY`,
`WAYLAND_DISPLAY` and `DBUS_SESSION_BUS_ADDRESS` removed, the configured JBR b635.75 ran
`./gradlew :jasper-app:compileTestJava`, passing in 472 ms (12 tasks; one executed).
Remote log: `/home/dustin/jasper-tray-gate.1yPuL3/task-2-compile.log`.
Compilation proves public API availability, not tray recovery.

Read-only preparation found `/usr/share/xsessions` empty and only `plasma.desktop` under
`/usr/share/wayland-sessions`. `lxpanel`, `lxsession`, `mate-panel`, `xfce4-panel`, `startlxde`
and `startxfce4` are unavailable; `xprop` and `xdpyinfo` are installed. No graphical/session
secrets were read and no panel/session state was changed. Plasma alone cannot satisfy this gate.

After explicit graphical authorization and provisioning of each required session, run from its
own graphical-session terminal (inherits its display and authentication without copying secrets):

```sh
cd /home/dustin/jasper-tray-gate.1yPuL3
jasper-app/build/packaging/image/Jasper/lib/runtime/bin/java \
  -Djava.awt.headless=false \
  -cp 'jasper-app/build/packaging/image/Jasper/lib/app/*:jasper-app/build/classes/java/main:jasper-app/build/classes/java/test' \
  dev.jasper.app.platform.LinuxTrayProbe
```

The runtime version was checked read-only: JBR `25.0.4.1+1-b635.75-jcef`. The command above
has **not** been launched. For each three-minute run, record distro/session/panel/JBR versions
and actual observations for the following rows; use a fresh run where the sequence exceeds three minutes.

| Required evidence | LXDE / X11 | MATE or XFCE legacy host |
|---|---|---|
| Start with no host; enable host and observe icon | PENDING | PENDING |
| Restart panel; original icon returns without reinstall | PENDING | PENDING |
| Remove/re-add tray applet; host-loss/support observations and recovery | PENDING | PENDING |
| Menu click (`menu action EDT=true`) and activation | PENDING | PENDING |
| Transparency and 1×/2× scale; no ghost/lost icon | PENDING | PENDING |

Capture probe stdout alongside visible behavior: `no host`, `added slot=…`, `removed`,
menu and activation lines. The probe intentionally retains `current` while support reports true;
that characterizes JBR's automatic recovery. A ghost/lost icon fails even if Java still lists it.
Assess whether public support/property signals suffice or remove/re-add would be necessary;
no result is inferred from compilation or from an icon appearing once. Task 6 stays blocked until
native evidence passes. Failed recovery requires a reviewed X11/JNA design amendment before fallback work.

Read-only isolation feasibility: `Xvfb`/`xvfb-run` (installed Xvfb `21.1.25-2.fc44.aarch64`),
`dbus-run-session`, `timeout` and `xrandr` are available. Cached Fedora package metadata confirms
these missing install package names (no installation performed):

- LXDE: `lxpanel` (`0.11.1^20260313git4dec3d0d-1.fc44`), `lxde-common`, `lxsession`, `openbox`.
- Second legacy desktop: `xfce4-panel` (`4.20.7-2.fc44`), `xfce4-session`, `xfwm4`.
- Observation/input: `xdotool`, `xwd`, `xwininfo`; optional visible nested display: `xorg-x11-server-Xephyr`.

Proposed authorization scope is installation of those prerequisites and isolated X11 runs using
Xvfb, a private `dbus-run-session`, temporary HOME/XDG configuration and only the test display's
panels. Actual LXDE and XFCE panels can exercise late host creation, restarts, applet changes and
menu/activation there, with stdout and captured pixels at 1×/2×. Xvfb screenshots can characterize
raster alpha/scaling; physical display/HiDPI appearance still needs user inspection. Neither Xvfb
nor a panel was launched during preparation. The package-provider file-list query lacked cached
metadata; exact package-name queries above succeeded. Any GUI/session run still requires authorization.

## Task 4 protocol and deadline evidence — 2026-10-27

On the retained isolated Fedora 44 aarch64/JBR 25.0.4.1-b635.75 workspace,
`./gradlew :jasper-app:test --tests '*LinuxStatusNotifierTest' --tests '*LinuxTrayWireTest'
verifyApplicationArchitecture verifyThirdPartyNotices` passes: 16 tests, zero failures/errors/skips;
37 shipped-module notices verified. The same local command plus `--tests '*ThemeMigrationCoverageTest'`
passes with 20 XML tests (6 executed, 14 Linux-only skips), architecture and notices. Only the four Task 4
sources and the reviewed Task 3 `LinuxTrayMenu` prerequisite were transferred. No desktop bus, GUI,
installed application, AOT cache or dependencies changed.

Both private filesystem and abstract buses pass actual Properties/method replies and independent
`gdbus introspect --xml` comparison of every item/menu property, method and signal signature.
Independent `gdbus call` records the pinned error names
`org.freedesktop.dbus.errors.UnknownInterface`, `org.freedesktop.dbus.errors.UnknownProperty` and
`org.freedesktop.dbus.errors.PropertyReadOnly`. Tests also cover filtered/depth-limited layouts,
empty/compound property variants, grouped events, stale senders, freedesktop alias, host absence,
subscription-before-snapshot, owner replacement during registration, publication-name loss,
captured-address bus restart, unresponsive registration and stalled authentication with peer-observed EOF.
A single admitted worker owns each attempt and cleanup; a scoped 1.9-second interrupt deadline leaves
headroom for the two-second publication result. No global method timeout is changed. The controller owns
retry timing; closing blocks new callbacks and returns without waiting for library teardown.

Controller Ruling 12: 5.2.0 marshalling rejects nongeneric Tuple return types, and unwrapping a compound
or empty property value loses its required Variant signature. Generic tuple carriers and returning the
explicit Variant from `Properties.Get` pass actual wire tests. Controller Ruling 13: released
`ExportedObject.createReturnArguments` emits generic tuple outputs in two consecutive loops; this
makes introspection advertise doubled outputs although the actual reply is correct. The adapter uses
public `ExportedObject` and `ObjectTree.add` APIs to remove exactly the duplicate output suffix from
`GetLayout` and `AboutToShowGroup` before registration. Any unexpected shape logs and refuses publication.
This version-specific metadata accommodation must be removed or revalidated on a dependency upgrade.
No reflection, replacement RPC layer or vendored library was introduced.

Review evidence is retained in `.superpowers/sdd/2026-10-27-linux-residency-2-tray/task-4-evidence/`:
RED logs, final Linux/local logs, native XML results, source SHA-256 identities and independent XML/error
outputs. Remote originals remain in `/tmp/task4-wire-final.log` and the isolated workspace's
`jasper-app/build/linux-tray-wire/`. The Task 1 source archive at
`/tmp/jasper-dbus-evidence/dbus-java-core-5.2.0-sources.jar` supplies the binding evidence.
Full `check`, desktop host acceptance, x86_64 and Ubuntu 22.04 CI remain assigned to their existing gates.

### Task 4 review fix 1

The handoff regressions deterministically pause the adapter at the former cleanup/admission gap.
Close and withdraw both failed to drop the publication before the fix. Cleanup requests now drain
before ownership is released under the same lock; close/withdraw return while connection cleanup
continues on the sole worker. A package-private Runnable seam supplies this latch boundary in tests;
the production constructor supplies a no-op. The two-private-bus regression starts the earlier
captured endpoint only after discovery falls back to the second bus, with both watchers deliberately
using the same unique name. It reproduced registration on the wrong bus. Publication now retains
the discovery endpoint and compares the daemon's `GetId` before registering. Introspection subprocesses
are forcibly terminated in `finally`, including failed waits/assertions.

`LinuxStatusNotifierTest` and `LinuxTrayWireTest` pass 19 tests on Fedora ARM64, zero failures/errors/skips.
The three new regression cases first failed on the prior implementation. Local focused/inventory tests
pass 22 XML tests (6 executed, 16 Linux skips); application architecture passes. Both final logs have
zero compiler warnings. Evidence is in `task-4-evidence/fix-1/` beside the original task report; no
full suite, graphical session, dependencies, installed app or packaging was changed.

## Task 5 controller and lifecycle verification

Fedora 44 aarch64, JBR 25.0.4.1-b635.75, isolated `/home/dustin/jasper-tray-gate.1yPuL3`:

```sh
env -u DISPLAY -u WAYLAND_DISPLAY -u DBUS_SESSION_BUS_ADDRESS \
  JAVA_HOME=/home/dustin/.sdkman/candidates/java/current \
  ./gradlew :jasper-app:test --tests '*LinuxResidentTrayTest' \
  --tests '*LinuxStatusNotifierTest' --tests '*LinuxTrayPolicyTest' \
  --tests '*LinuxTrayWireTest' verifyApplicationArchitecture --console=plain
```

**PASS:** 50 tests, zero failures/errors/skips: controller 27, adapter 2, policy 1, wire 20; architecture passes. Existing wire coverage runs again for both private-bus address forms. New cases prove bus connection loss/restoration even with identical no-host snapshots, confirmed withdrawal before replacement, two-second withdrawal timeout retaining owed cleanup and independent daemon name loss after confirmation. Controller tests use manual queues/latches without sleeps and cover owner replacement during registration, queued stale actions, late legacy support loss, duplicate healthy observations, 10,000-event coalescing, 1/2/4/8/16/30/30 backoff/reset, fallback restoration, every adapter failure/cleanup path and close during blocked work.

Local macOS command adds `--tests '*ThemeMigrationCoverageTest'`: 52 XML tests, zero failures/errors, 18 Linux-only skips (34 executed); architecture passes. The new controller test needs one nonvisual source-inventory row. Final local/native compiler output has zero warnings. `git diff --check` and Java source hygiene pass.

Ruling 14 expands the internal Modern record with `Runnable reconnect`, bound to `LinuxStatusNotifier.start`, and reports connection state independently of Host. The controller owns the sole capped reconnect timer; successful connection with no watcher resets backoff and cancels retries. Ruling 15 adds `withdrawAndAwait`, worker-only and bounded to two seconds, while preserving nonblocking `withdraw` for cancellation. Task 6 must bind confirmed withdrawal before fallback installation. Cleanup timeout refuses replacement and retains the cleanup request.

Task 5 implements injected scheduling with logical timer invalidation. Task 6 still owns production physical timer cancellation/removal on success, restoration and close; daemon scheduling; five-second legacy probing suspended while modern publication is healthy; and bounded EDT marshaling/late-add cleanup. Task 2's native AWT gate is pending, as are x86_64/Ubuntu baseline CI, graphical acceptance and final integration checks. No GUI, actual desktop session bus, installed-app change, AOT, merge or push was used.

### Task 5 review fix 1

Commit `2d291bb18` records the installed legacy action generation and refreshes a stale publication after
coalesced host/support reversals. Both deterministic transition regressions failed before the fix; the
healthy-duplicate case already passed. The final portable controller/policy run passed 31 tests with no
failures, errors or skips. Scoped re-review approved all findings with no new breakage. The native
50-test run above predates this controller-only fix; the adapter is unchanged. Tasks 1, 3, 4 and 5 are
reviewed. Task 2's compiled probe has installation/GUI authorization as of 2026-10-28 but awaits prerequisite installation access (below), so Task 6
production composition, Task 7 final checks and whole-branch review remain pending.


## Task 2 authorized native attempt — 2026-10-28

The user approved installing the exact prerequisite list and running isolated Xvfb probes with
temporary HOME/XDG directories, private buses and no active Plasma changes. The authorized attempt:

```sh
ssh -o BatchMode=yes -o ConnectTimeout=10 dustin@10.211.55.13 \
  'sudo -n dnf install -y lxpanel lxde-common lxsession openbox xfce4-panel xfce4-session xfwm4 xdotool xwd xwininfo'
```

Exited **1** immediately with `sudo: a password is required`. Raw output is retained at
`/home/dustin/jasper-tray-gate.1yPuL3/task-2-install.log`. No package was installed, no graphical
probe/display/panel/session was started and no credentials were read. The native gate remains
**PENDING**, not failed recovery: no recovery observation was possible. Required next step is
installation of the already-approved package list by the user or narrowly scoped installation
access. Graphical authorization remains granted; it need not be requested again for this scope.

## Task 2 native result — 2026-10-28: public AWT gate FAILED

The user installed the approved packages after the earlier noninteractive sudo failure. Versions:
`lxpanel 0.11.1^20260313git4dec3d0d-1.fc44`, `lxde-common 0.99.3-6.fc44`,
`lxsession 0.5.6-4.fc44`, `openbox 3.6.1-30.fc44`, `xfce4-panel 4.20.7-2.fc44`,
`xfce4-session 4.20.3-2.fc44`, `xfwm4 4.20.0-4.fc44`, `xdotool 3.20211022.1-10.fc44`,
`xwd 1.0.9-5.fc44`, `xwininfo 1.1.6-4.fc44`; all aarch64 except noarch lxde-common.
Host remains Fedora 44 aarch64, Xvfb `21.1.25-2.fc44`, packaged JBR
`25.0.4.1+1-b635.75-jcef`. No installed Jasper was run or changed.

Each run used only an owned Openbox/LXPanel or xfwm4/XFCE panel, a separate `dbus-run-session`,
Xvfb `1024x768x24 -nolisten tcp`, and temporary HOME/XDG config/cache/data/runtime (runtime mode 0700).
Full desktop sessions/autostarts were excluded; active Plasma's display, bus and panels were untouched.
The harness records owned child PIDs, terminates/waits only those processes, exits its private display/bus
and removes its temporary home. Displays :101–:121 were private test displays, not Plasma.
Commands follow this pattern, with the preserved harness supplying the actual panel configuration,
known PIDs, input, screenshots and cleanup:

```sh
env -u DBUS_SESSION_BUS_ADDRESS -u DISPLAY -u WAYLAND_DISPLAY -u SESSION_MANAGER \
  HOME="$taskhome" XDG_CONFIG_HOME="$taskhome/config" XDG_CACHE_HOME="$taskhome/cache" \
  XDG_DATA_HOME="$taskhome/data" XDG_RUNTIME_DIR="$taskhome/run" \
  NO_AT_BRIDGE=1 GIO_USE_VFS=local \
  dbus-run-session -- xvfb-run --server-num=101 \
  -s '-screen 0 1024x768x24 -nolisten tcp' \
  python3 task-2-native.py lxde 1 native-task2/lxde-1-corrected
```

**Fixture correction (Ruling 16).** The original exact probe exited 0 in **1.39 seconds** after
one `no host` line: Swing Timer alone did not keep the no-window JVM alive. Its main now waits on
a bounded 185-second latch; the original 180-second timer still removes the icon and exits 0,
with exit 1 as a bounded fallback. Corrected no-host runs stayed alive past five seconds and discovered
late hosts. Local headless compile/inventory passed (8 seconds; **5 tests, 0 failures/errors/skips**),
and Linux compile passed (3 seconds). Existing `UnexpectedExceptionsTest` passed **3/3** locally.
This is test-only lifecycle preparation, not proof of Jasper residency.

| Observation | LXDE/Openbox | XFCE/xfwm4 |
|---|---|---|
| No host, then panel introduced | Icon appeared; support false → true | Icon appeared; support false → true |
| Panel stopped for five seconds and restarted | Icon returned; actions/menu still worked; uncaught exception below | Icon returned; actions/menu still worked |
| Rapid panel replacement | Retained original icon in diagnostic run, add count 1 → 1 | Usually public removal + re-add, add count 1 → 2 |
| Actual live applet removal/re-add via panel preferences | Empty list/no icon, then full icon and actions/menu restored | Empty list/no icon, then full icon and actions/menu restored |
| Menu/activation | `activate`, `menu action EDT=true` before/after recovery | Same |
| Normal 1x and matched 2x rendering | Full orange circle, transparent corners | Full orange circle, transparent corners |

Live UI evidence is in `native-task2/live-{lxde,xfce}-1`: observed preference controls removed
System Tray / Status Tray Plugin and re-added the same actual applet. Both produced public membership
0 / unavailable notifications and restored membership 1, activation and EDT menu callbacks. Earlier
`*-supplement`/`final-*` XFCE stages named `applet-live-removed` only deleted xfconf properties;
the running plugin stayed visible. Those stages are **excluded** as live-removal evidence. The
initial interactive `ui-*` runs confirmed removal but reached their timer before re-add; the automated
`live-*` runs completed both using controls derived from those captured private-panel screenshots.

**Decisive failure:** LXDE panel destruction/restart with a published icon produces:

```text
Exception in thread "AWT-EventQueue-0" java.lang.UnsupportedOperationException: The system tray is not supported on the current platform.
	at java.desktop/java.awt.SystemTray.getSystemTray(Unknown Source)
	at java.desktop/sun.awt.X11.XTrayIconPeer$3$1.run(Unknown Source)
	at java.desktop/java.awt.event.InvocationEvent.dispatch(Unknown Source)
	at java.desktop/java.awt.EventQueue.dispatchEventImpl(Unknown Source)
	at java.desktop/java.awt.EventQueue.dispatchEvent(Unknown Source)
	at java.desktop/java.awt.EventDispatchThread.pumpOneEventForFilters(Unknown Source)
	at java.desktop/java.awt.EventDispatchThread.pumpEventsForFilter(Unknown Source)
	at java.desktop/java.awt.EventDispatchThread.pumpEventsForHierarchy(Unknown Source)
	at java.desktop/java.awt.EventDispatchThread.pumpEvents(Unknown Source)
	at java.desktop/java.awt.EventDispatchThread.pumpEvents(Unknown Source)
	at java.desktop/java.awt.EventDispatchThread.run(Unknown Source)
```

JVM liveness,
subsequent icons and menus recover; the exception is still material. Jasper's
`ApplicationBootstrap.UnexpectedExceptions` logs ERROR and forwards to `JasperApplication.unexpected`,
then `IncidentNotices.jasperFailure`, which records and announces a Jasper incident. No Jasper GUI
was needed to establish that routing; the focused existing three-test handler suite passed.

A disposable public-only callback candidate used the cached `SystemTray` owner, removed the current
icon immediately on the EDT when documented `systemTray` became null, and reconciled current identity
against `getTrayIcons()` on documented `trayIcons` notifications. It did **not** prevent the LXDE
exception. `notify-lxde-original-gdk1-dpi96-auto1/probe.log` records exception **before** the public
unavailable callback at `2026-10-28T13:28:48.872743004Z`, then membership 0 at `.877812619Z`, available
again at `.909885181Z`, and a later successful add/action. The same order recurred at panel stop.
Live applet removal/re-add with these callbacks succeeded without that exception; panel destruction
remains the failed case. No exception suppression, internal imports or reinstall loop was added.

Pinned JBR `lib/src.zip` explains the observed race: `XTrayIconPeer` queues a reparent-to-root
cleanup that calls `SystemTray.getSystemTray().remove(target)`; `XSystemTrayPeer.ownerDeath` separately
queues the public `systemTray` notification and peer removal. A listener cannot cancel cleanup already
queued ahead of it. Public membership notifications detect auto-removal (seen on XFCE), but the tested
public listener did not make the LXDE panel-loss path incident-free. Extracted source and hashes are
retained under `native-task2/source-{SystemTray,XSystemTrayPeer,XTrayIconPeer}.java` and `source-sha256.txt`.

**Scale qualification:** public `getTrayIconSize()` reported 24x24 throughout; actual XEmbed slots
were 24x24 LXDE / 22x22 XFCE at 1x, and 48x48 / 44x44 at matched 2x. Diagnostics recorded public default
transforms 1 and 2 respectively. Matched 2x uses `GDK_SCALE=2`, Xft.dpi=96 and LXDE `iconsize=48`
(panel height 64); normal orange bounds double from 18x17 pixels (242 orange pixels) to 36x34 (968).
The screenshots show panel-colored transparent corners, no black opaque square or duplicate/ghost icon.

Forcing only `-Dsun.java2d.uiScale=2` while the host remains 1x clips the icon in both panels, while the
menu scales. Xft.dpi=192 alone with GTK2 LXDE retained public transform 1; combining GDK_SCALE=2 and
Xft.dpi=192 auto-detected transform 4 (double scaling), not a normal 2x acceptance case. A disposable
explicit-size/no-autoSize candidate fixed the small-host crop but was undersized on a scaled host;
MultiResolutionImage retained the crop. One explicit-size XFCE rapid-restart variant also lost its
icon (blank screenshot, no JavaEmbeddedFrame, retained reference/add count 1 → 1). These variants are
rejected; none is production code or evidence for a universal fix. Virtual-display raster checks do
not establish physical-monitor HiDPI appearance.

Selected original screenshots are retained in [the evidence folder](2026-10-27-linux-residency-2-evidence/):
`lxde-2x.png`, `xfce-2x.png`, `java2-panel1-clipped.png`, `variant-lost-icon.png`,
`lxde-live-removed.png` and `lxde-live-restored.png`. Complete PNG/XWD/window trees, probe/panel logs,
PID records and fixture scripts remain under `/home/dustin/jasper-tray-gate.1yPuL3/native-task2/`,
`task-2-*.{py,sh}` and `runner/LinuxTray{Diagnostics,Notifications}.java`; local selected/full copies
are under `/tmp/jasper-task2-native-evidence/`. The initial unmodified run and independent timing are
`lxde-1/` and `original-nohost-timing.log`; normal accepted configurations are `final-*` plus
`lxde-1-corrected` / `xfce-1-corrected`. `notify-*` contains the decisive failed callback mitigation.

**Gate disposition:** FAIL for an incident-free public-AWT legacy fallback on this pinned JBR.
Stop Task 6's AWT composition and review the plan-required X11/JNA legacy design amendment; do not
silently drop legacy support or hide the native exception. Protocol Tasks 1/3/4/5 remain independent.
No production fallback, Jasper GUI, benchmark, AOT, merge or push was performed.

Cleanup audit: all **113 recorded owned PIDs** were gone, and no test display socket/lock for :101–:121 remained. Eight temporary homes contained only late Mesa shader caches after initial cleanup; those exact audited directories were removed. Final temporary-home list is empty. Raw audit: `native-task2/cleanup-audit.json`. Both automated live-app probe runs exited 0 at their normal three-minute deadline.

## Task 6 owned XCB transport — 2026-10-28: Fedora aarch64 gate passed

Implemented the concrete worker-owned XCB boundary, local DISPLAY/setup parsing, bounded authority framing and
native Xau decoding, cooperative regular-file acquisition, wake pipe, protected deadline duplicates, scoped
replies/errors and bounded event preservation. The opt-in probe uses private protocol fixtures and watchdog
children; no display server, panel, desktop bus or native window is required. Production routing remains Task 9.

The user approved the [authority-I/O correction](../specs/2026-10-28-linux-x11-tray-amendment-design.md#authority-io-correction--approved-2026-10-28).
Held open/read fixtures prove one owned daemon attempt, responsive cancellation, no late connection, and cleanup
after release. They do not prove interruption of filesystem syscalls. A real stalled syscall may retain one worker
and authority FD plus its wake pipe until kernel return. Modern publication and Quit must remain independent in
Tasks 7/9. Only final-component symlinks are rejected; normal local regular files are the operational scope.

Fedora 44 aarch64 used JBR `25.0.4.1+1-b635.75`, libxcb/libxcb-devel `1.17.0-7.fc44.aarch64`,
libXau/libXau-devel `1.0.12-4.fc44.aarch64` and glibc-devel `2.43-9.fc44.aarch64`. Matching public headers were
already installed; no RPM extraction or system installation was needed. The C helper compiles with `cc -std=c11
-Wall -Wextra -Werror`, `pkg-config --cflags xcb xcb-render xau`, and no native library link flags. Its 102 exact
sizes/offsets/constants match the Java boundary, including 8-byte pointer/long, 4-byte XID/cookie, auth/Xauth,
stat identity/timestamps and wire layouts. **Ruling 21:** the C gate and symlink regression disproved the plan's
common `O_NOFOLLOW=0x20000`: aarch64 requires `0x8000`; x86_64 retains `0x20000`. Unknown architectures fail
unavailable. Both supported ABIs require their own native assertion; x86_64 is still CI-pending.

Commands executed under `/home/dustin/jasper-tray-gate.1yPuL3`, with
`JAVA_HOME=/home/dustin/.sdkman/candidates/java/current`:

```sh
./gradlew :jasper-app:linuxTrayNativeTest --tests '*LinuxTrayXcbTest'
./gradlew :jasper-app:linuxX11TrayProbe --args='--gate transport --require-native --output build/tray-gate/transport' verifyApplicationArchitecture
```

Final focused native XML: **21 tests, zero failures/errors/skips**. The last full watchdog execution passed
**20 children, zero required skips, zero watchdog failures**: ABI, held authority, 32 forced stale-callback FD-reuse
iterations, four fail/reconnect cycles (BadAuth/BadWindow/BadMatch/EOF), and setup/unanswered-reply/explicit-flush/
implicit-flush stalls with both socket forms and timeout/close termination. Backpressure reached `EAGAIN` after
233,152 bytes; active-native-call acknowledgements verified actual explicit/implicit stalls. Timeout phases
completed in 2,005–2,012 ms; close cases completed in 110–116 ms including the deliberate 100 ms stall observation.
Every child returned to 45 FDs and eight live thread names; live duplicates/auth records, stranded workers and
unexpected exceptions were zero. Every replacement socket remained usable after its delayed old deadline callback.
JNA auth allocations, Xau records/streams and XCB reply/event allocations have explicit release assertions.

Self-review RED regressions exposed and fixed event loss at barriers, same-size authority changes, idle-poll timeout
handling, old-connection double-close releasing a later attempt slot, and cumulative retained-event byte limits.
The final two fixes were followed by the complete 21-test focused native run; the 20-child watchdog evidence precedes
those two small fixes. No broad suite was repeated. Local macOS focused boundary/inventory checks and
`verifyApplicationArchitecture` pass: **26 XML tests, nine executed, 17 Linux skips**, zero failures/errors.
Three discovered source-inventory rows were added. Source hygiene and `git diff --check` pass.

Raw evidence: remote `native-{expanded,gate,review-red,review-green-idle-red,final,last-review-red,last-review-green}.log`,
`jasper-app/build/tray-gate/transport/{summary.txt,abi-c.txt,abi-java.txt,*.log}` and
`jasper-app/build/test-results/linuxTrayNativeTest/TEST-dev.jasper.app.platform.LinuxTrayXcbTest.xml`.
Local copies and portable RED/GREEN logs are in `.superpowers/sdd/2026-10-27-linux-residency-2-tray/task-6-native-evidence/`;
`task-6-report.md` records the implementation handoff. Fixture sockets/path reservations, workers and descriptors
were closed; watchdog children exited normally. Active Plasma and actual authority credentials were untouched.
No Jasper GUI, AOT, benchmark, push or merge ran. Native legacy hosts remain Task 7, production ownership/composition
Tasks 7/9, and final full check/packages/packaged-runtime proofs Task 10. x86_64 and Ubuntu 22.04 remain required CI
release gates. Independent per-task review is pending.

## Task 7 owned XEmbed — 2026-10-28 Fedora native gate passed

Implemented `LinuxLegacyTray` and extended the concrete `LinuxTrayXcb` worker boundary for XEmbed, RENDER and input. Constructor remains native-free; install/remove have two-second caller bounds, cleanup refusal blocks replacement, and close invalidates without waiting. Host/connection admission guards queued actions. No popup or production fallback routing is included; independent review precedes Tasks 8–9.

**Controller Ruling 24:** use unchecked generated void mutations with retained full sequences and a bounded reply barrier/error drain. Installed public headers specify that checked void errors require `xcb_request_check`, conflicting with the approved nonblocking boundary. Async errors, including unrelated/stale errors, fail this owned connection; ordinary events remain queued. Cost: conservative connection invalidation/redock on unexpected errors. Wire regressions cover create/destroy error delivery, ordinary event retention, exact metadata/messages and scanline uploads under a 256-byte server request limit.

**Controller Ruling 25:** advertise minimum2x2, preferred24x24 and maximum512x512; assigned slots1..512 remain valid. [Official XFCE source](https://github.com/xfce-mirror/xfce4-panel/blob/51a7d2a9526f8a9f4f9cadabd85eae3dd56bb1c3/plugins/systray/systray-box.c) treats both requested dimensions<=1 as invisible and moves the socket offscreen. Before: native icon at(-9999,-9999), plugin width1. After: visible allocated slot and native input pass. This corrects the plan's interoperability defect without forcing24px slots. Optional Ruling23 pure pixel-helper split was not used; native ownership stays inside `LinuxTrayXcb`.

| Evidence | Result |
| --- | --- |
| Fedora44 aarch64, JBR 25.0.4.1-b635.75, final native task | 46 tests:29 XCB +17 adapter;0 failures/errors/skips |
| Controller/inventory regressions and architecture | 30 controller +5 inventory tests;0 failures/errors/skips; architecture passes |
| Existing transport watchdog gate after binding changes | 20 required cases;0 skips/failures |
| Expanded installed-header C/JNA ABI helper | 137 exact values, including35 new core/RENDER offsets; FD45→45, threads8→8,0 duplicates/auth records/stuck workers/unexpected exceptions |
| Local macOS focused/inventory + architecture | 51 tests:14 executed,37 Linux-only skips;0 failures/errors |
| LXDE opaque, compositor off | 24/24 cells:16/24/32/48 slots × light/dark/pattern × JBR scales1/2 |
| XFCE advertised alpha visual912, private compositor on | 24/24 cells, same matrix; actual native dimensions match requested slots |
| Additional compositor checks | LXDE opaque16/pattern/JBR2 with compositor on passes; XFCE alpha/compositor-off declines cleanly with no icon/pending publication |
| Real host death/restart | 20 alternating slow/fast cycles per panel; old resources removed, one replacement icon, stale actionzero |
| Actual live tray applet removal/re-add | Both panels pass through preferences UI with the **same panel PID alive**; separate from restart rows |
| Actual Xvfb selection replacement/dock loss | Both panels stay alive; supplementary private owner replaces selection, receives actual dock, validates successful reparent/notify, then loses selection during a second pending dock; false only after cleanup; stale menu actionzero |
| Final replay after unmap fix | Both panels repeat2 fast/slow cycles plus input, live applet and selection/dock/menu rows |

The native tests reproduce and fix a close-lock bug (close previously waited1.69s during dock cleanup) and an embedder-unmap admission bug. Unmapping now cancels popup/queued admission and pauses input until remap. An acknowledged held-native-destroy regression proves removal throws after its bound, another install is refused and close stays nonblocking until the worker can finish. The approved authority-only exception remains unchanged. An unsupported-slot fixture now awaits server EOF acknowledgement after local disconnect; no cleanup guarantee was relaxed.

Native outputs are retained in authorized stage `/home/dustin/jasper-tray-gate.1yPuL3`: `jasper-app/build/tray-gate/task7-matrix/lxde-*`, `task7-framebuffer-matrix/xfce-*`, `task7-lxde-cycles`, `task7-xfce-framebuffer-cycles`, `task7-lxde-final-replay`, `task7-xfce-final-replay`, `task7-alpha-off`, `task7-opaque-compositor-on`, `task7-transport-regression`, `task7-expanded-abi`. Command logs are under `build/tray-gate/task7-*.log`. Local implementation report and selected evidence are under `.superpowers/sdd/2026-10-27-linux-residency-2-tray/task-7-report.md` and `task-7-native-evidence/`.

The source audit compared **3,956** tracked/new source and build inputs across app/controller/native fixtures and dependent modules/plugins; all local/remote SHA256 values match. Final manifest `task7-input-hashes-final.json` has SHA256 `a2e4c31c96be2f208dcc68402bdf3979f13d847868c315415fe3a22082ff4e1e`. An earlier remote controller copy lacked the reviewed Task5 generation fix; that composition failure is excluded, and the full input audit plus final composition tests replaced it. Production inputs are unchanged since final checks/replays; the final test-only ABI expansion was compiled and executed separately.

Visual acceptance uses full raster composition/corner comparisons with tolerance3/channel and no removal trails, plus inspected screenshots. Raw root XGetImage is unsuitable for depth32 inferiors of a depth24 root: [Xlib documents undefined different-depth inferior pixels](https://xorg.freedesktop.org/releases/current/doc/libX11/libX11.html). The harness preserves root/native-icon captures and captures the owned Xvfb `-fbdir` framebuffer for composited output. It moves the cursor outside the comparison region and forces RGB PNG24 output: otherwise ImageMagick's grayscale optimization causes Java ImageIO to convert encoded238 to247, creating a false delta9. Those failed captures are retained and excluded; renderer upload was not changed to accommodate them.

Optional compositor `xcompmgr1.1.10-4.fc44.aarch64` was obtained by authorized rootless Fedora RPM extraction, with signature/digests and resolved dependencies verified; no system install. RPM SHA256 `68abc55104501474b99978459fe2da761fc02c40d643999841b772894fc37963`, executable SHA256 `db657142b0835039126be5de311d362d19a6bcca6802747c921ca2a2feb10331`. It is a test tool, not a production dependency.

**Excluded isolation failure:** the first XFCE attempt inherited Wayland settings and its log queried compositor capabilities before it failed to provide a tray owner. It is not evidence that the active compositor was untouched. Owned processes were terminated and private HOME removed. Subsequent runs explicitly set private DISPLAY/bus/HOME/XDG_CONFIG/DATA/CACHE/RUNTIME, remove WAYLAND_DISPLAY/WAYLAND_SOCKET/SESSION_MANAGER and force X11 backends/session type. Finally cleanup joins the native worker, stops only owned processes/descendants and removes private homes; each accepted row records `alive=0 privateHomeRemoved=true`. Xvfb/private-bus wrappers own server/socket cleanup. No active desktop configuration was edited.

No actual Jasper GUI, AOT, benchmark, login install, merge or push was performed. Virtual mixed host/JBR scales leave physical mixed-monitor acceptance pending. x86_64, Ubuntu22.04 and packaged-runtime acceptance remain the approved later CI/Task10 gates.


## Task 8 transient popup — 2026-10-28 BLOCKED

Prepared placement, bounded EDT admission, guarded shared menu actions and one transient dialog without production routing. Ruling 26 retains native root dimensions for verified single-device mapping; multi-device identity remains declined. Fedora focused checks pass: 30 XCB +21 adapter native tests, 14 popup +30 controller +5 inventory portable tests, zero failures/errors/skips, and architecture. Local checks execute 59 of100 tests, with41 Linux-only skips and zero failures/errors.

The isolated LXDE/Openbox/JBR b635.75 gate fails: first POPUP opens, focuses and selects Open Jasper once; the second maps but never gains keyboard focus. Public requestFocus, root-pane focus on opened and toFront did not repair it. Ordered events/native logs show the prior disposed dialog still focused and new native focus requests using AWT's prior user timestamp. Native icon input occurs on the independent XCB connection. POPUP has override-redirect, no WM client-list entry and zero taskbar-region pixel changes. A managed UTILITY characterization restores repeated focus/actions but visibly adds a **Jasper tray menu** taskbar button (3556 changed taskbar pixels); it is excluded as an implementation. No remaining popup matrix or production acceptance is claimed.

All attempts use an owned Xvfb framebuffer, private HOME/XDG/runtime0700/bus and forced X11 environment; owned-process cleanup reports alive0 and private home removed. The failed gate and proposed narrow managed-popup skip-taskbar/skip-pager correction are recorded in `.superpowers/sdd/2026-10-27-linux-residency-2-tray/task-8-report.md` and `task-8-proposed-correction.md`. The proposed correction is not approved or implemented. Task 8 remains uncommitted.

### Task 8 final correction — 2026-10-28 Fedora gate passed

The user-approved managed-popup correction and controller Rulings26/28–30 add bounded own-peer skip hints and one advertised source1 WM-mediated activation request with the actual initiating press time. No direct XSetInputFocus, grab, private AWT state or active-window property write is used. Failed USER_TIME metadata variants were removed after JBR map-time overwrite and Openbox's lack of redirection support were observed. Native peer leases span preparation/activation completion or connection termination, including cancellation; focus denial has a2s bound and hide/focus-loss/dispose retirement a1s fallback.

LXDE/Openbox and XFCE/xfwm4(--compositor=off), with an owned rootless compositor for XFCE alpha, pass JBR scales1/2. Each row executes35 transient menus: all four callbacks exactly once, vetoed Quit reopen, Menu/Shift-F10, traversal and Escape/outside/focus loss,20 reopen cycles, moved pointer, four fresh-gesture edge placements and host loss, plus queued-close zero native submissions. The full1280x32 panel framebuffer strip is pixel-identical before/during the menu in all four rows, providing actual taskbar/pager evidence. Native JVMs exit naturally. After bounded GC, at most one disposed raw Window entry remains; displayable/focused/active windows and focus owner are zero, production popup references empty, menu path empty, and toolkit listeners equal the sole process Metal LAF helper baseline. This narrows the raw-count oracle without claiming an exact raw baseline.

Final focused Fedora checks pass109 tests(55native+54portable),0failures/errors/skips, and architecture. Local109 tests execute64 with45 Linux-only skips,0failures/errors. Source audit3958inputs matches; manifest SHA256 `b9e113026d7a9c18742df833c88ed4367da41499f3cc5f88b9556870afbacacb`. No full check until Task10.

Excluded observations remain disclosed: original POPUP focus failure; unhinted UTILITY taskbar entry; own/redirected USER_TIME failures; a100ms native poll deadline under verbose focus logging (transport unchanged); and a Task7 artwork before/after oracle made inequivalent by added taskbar/pager widgets. Popup mode uses an already-embedded baseline; legacy artwork validation is unchanged. One outer-wrapper private runtime/doc cleanup failed after its probe JVM passed; the exact known path was subsequently already unmounted and was removed. Global desktop portals were untouched. The retained wrapper now requires a task owner marker, runtime/start-time matched helper descendants and verified exact portal mount source before cleanup. Physical mixed-monitor and later package/x86_64 acceptance remain pending. Implementation review is required before Task9.


## Task 9 production composition — 2026-10-28 Fedora gate passed

Linux production routing composes `LinuxTrayRuntime`, the reviewed controller, StatusNotifier adapter, owned XEmbed adapter and transient popup. It begins from the existing settled indicator hook. Cold constructors and headless routing start no discovery. Confirmed modern publication cancels the sole five-second legacy probe; confirmed withdrawal resumes discovery with a fresh snapshot before installation. One daemon controller scheduler physically removes canceled timers. Close invalidates popup/action/adapter admissions immediately and lets owning workers drain resources before scheduler shutdown. Linux notifications continue through `LinuxNotifications`.

Final focused local command (`/tmp/jasper-task9-final-local.log`) passed in 20 s: 263 XML tests,0 failures/errors, 67 Linux-only skips (196 executed), and application architecture. Fedora aarch64/JBR 25.0.4.1-b635.75 (`build/tray-gate/task9-final-native.log`) passed in 1 m 18 s: 57 required-native tests without skips,267 focused tests with one existing macOS-only `ApplicationBootstrapTest.reconcilingTheLoginItemWritesAndRemovesIt` skip, architecture, and 20 transport watchdog cases with zero required skips/failures. The scoped suite includes runtime 10, controller 35, indicator 13, legacy 25 and XCB 32 tests. The held-authority regression composes a single deliberately held real transport worker with a real private-bus modern adapter; modern registration and EDT Quit succeed before release, followed by zero late connections and native cleanup. Its legacy function seam does not claim an actual blocked kernel syscall. No full project check was repeated; Task 10 owns it.

Both final composed native probes pass at 1x: LXDE/Openbox and XFCE/xfwm4(--compositor=off) with the owned rootless xcompmgr. Real popup captures were inspected. Each proves one legacy icon, watcher-without-host retention, confirmed legacy destruction before modern registration, modern preference with no periodic timer, stale legacy action rejection, modern Activate through the EDT guard, confirmed modern connection withdrawal before legacy recovery, popup reopen and prompt EDT close. An independent modern host fixture runs on a second owned private bus; exact daemon GetId values differ from the panel bus. This prevents XFCE's real modern watcher from replacing the controlled fixture. The earlier legacy-only XFCE oracle failed while the production runtime correctly published modern; that trace is retained as excluded evidence.

Controller Ruling 32 addresses a separate ordinary-load failure first observed in the XFCE probe: the 100 ms idle-poll budget expired during `xcb_poll_for_event`, causing erroneous host loss. A deterministic 150 ms delay at the real native boundary reproduced `[false,true,false]` instead of the healthy `[false,true]`; the RED native test failed, then passed after the correction. Adapter polling now supplies the existing 1.75 s operation absolute deadline independently of the 100 ms idle-wait deadline. Protected-duplicate shutdown, bounded native calls and the existing one-argument transport semantics remain intact. Final native tests/watchdogs and both panel replays pass. The prior failed logs remain separate.

### Reproducing the private environment

The final retained wrapper is `build/tray-gate/task9-runtime.sh PANEL` in the authorized stage `/home/dustin/jasper-tray-gate.1yPuL3`; its exact script and ownership-scoped cleanup helper are retained in the Task9 evidence. Before wrappers, it creates a marked private HOME/config/cache/data and runtime 0700. Launch order is **private environment → Xvfb → private D-Bus → probe JVM**, so bus-activated helpers inherit the private DISPLAY and XAUTHORITY. The effective command is:

```sh
env -u WAYLAND_DISPLAY -u WAYLAND_SOCKET -u SESSION_MANAGER -u DBUS_SESSION_BUS_ADDRESS -u DISPLAY -u XAUTHORITY \
 HOME="$private" XDG_CONFIG_HOME="$private/config" XDG_CACHE_HOME="$private/cache" XDG_DATA_HOME="$private/data" XDG_RUNTIME_DIR="$private/runtime" \
 GDK_BACKEND=x11 QT_QPA_PLATFORM=xcb XDG_SESSION_TYPE=x11 GIO_USE_VFS=local NO_AT_BRIDGE=1 GRADLE_USER_HOME=/home/dustin/.gradle \
 JAVA_TOOL_OPTIONS=-Dsun.java2d.uiScale=1 JASPER_TRAY_FRAMEBUFFER="$PWD/build/tray-gate/$cell-fb/Xvfb_screen0" \
 xvfb-run -a -s "-screen 0 1280x800x24 -fbdir $PWD/build/tray-gate/$cell-fb" \
 dbus-run-session -- timeout 90 ./gradlew --no-daemon :jasper-app:linuxX11TrayProbe \
 --args="--gate runtime --panel $panel $compositor --cycles 0 --slot 24 --background pattern --require-native --output build/tray-gate/$cell"
```

`panel` is `lxde` or `xfce`; `cell` is `task9-runtime-$panel`; `compositor` is empty for LXDE and `--compositor $PWD/build/tray-gate/tools/root/usr/bin/xcompmgr` for XFCE. The wrapper fixes JAVA_HOME to the staged JBR. Captures use the owned Xvfb framebuffer as PNG24. Inner cleanup records all owned panel/WM processes ended and private home removed. Final outer cleanup records ownedPids=[],mounts=[],removed=true, after matching the marker, private runtime/environment and process start times. Existing portal fallback/PipeWire warnings remain harness observations; missing-display activation errors from the old wrapper ordering are absent. Shared portals and `/run/user` were untouched.

All 3960 source/build inputs match local/Fedora; manifest SHA256 `a004f0c97af2cf7da1839413b8fd429dcc917ee47d3eb5fd4c154c2f37408bb5`. Full logs, RED/GREEN results, XML, accepted/excluded captures, exact wrapper/cleanup and manifests are retained under `.superpowers/sdd/2026-10-27-linux-residency-2-tray/task-9-evidence/`. The task report records implementation/self-review details. Task 9 independent review passed with no blockers; Task 10 retains package/CI/full-check acceptance and the physical/user-desktop rows.

## Task 10 CI and packaged runtime — 2026-10-28

Task 9 passed independent review with no blockers. Task 10 adds required wire/private-bus, native ABI/cancellation and transport watchdog gates before packaging on both existing Ubuntu 22.04 native runners. Missing reports, skips or failed required rows fail CI; failure artifacts include gate/ABI/XML/wire evidence. Existing package installation/coexistence and unrelated desktop-color SDK jobs remain. The workflow has not run: x86_64 and Ubuntu 22.04 results remain pending.

Controller Ruling 33 permits the existing probe's test harness beside test output: **AssertJ 3.27.7, JUnit Jupiter API 6.1.3, JUnit Platform Commons 6.1.3 and opentest4j 1.3.0**. `-Pjasper.tray.packagedHome=<absolute image>` sets JavaExec's executable to the image's `lib/runtime/bin/java`; production classes/dependencies resolve only from the image's 24 direct `lib/app/*.jar`. The task rejects other harness coordinates/versions and production overlap and prints its executable/classpath. No main classes directory or development runtime dependency jar is appended. Removing packaged `jna-5.14.0.jar` from the execution classpath fails transport; removing `dbus-java-core-5.2.0.jar` fails bus smoke. Neither production jar is supplied by the harness. The assertion-library whitelist is an explicit maintenance obligation, not a shipped dependency.

Final authorized Fedora commands, stage `/home/dustin/jasper-tray-gate.1yPuL3`, `JAVA_HOME=/home/dustin/.sdkman/candidates/java/current`:

```sh
./gradlew :jasper-app:test --tests '*LinuxTrayTransportTest' --tests '*LinuxTrayWireTest' --tests '*LinuxStatusNotifierTest' \
 :jasper-app:linuxTrayNativeTest --tests '*LinuxTrayXcbTest' --tests '*LinuxLegacyTrayTest' \
 :verifyThirdPartyNotices :verifyApplicationArchitecture :jasper-app:packageApp :jasper-app:verifyPackage -Pjasper.aot=false
image="$PWD/jasper-app/build/packaging/image/Jasper"
./gradlew :jasper-app:test --tests '*LinuxTrayTransportTest' --tests '*LinuxTrayWireTest' --tests '*LinuxStatusNotifierTest' \
 :jasper-app:linuxTrayNativeTest --tests '*LinuxTrayXcbTest' --tests '*LinuxLegacyTrayTest' \
 :jasper-app:linuxX11TrayProbe -Pjasper.tray.packagedHome="$image" \
 --args='--gate transport --require-native --output build/tray-gate/task10-final-packaged-transport'
timeout 60 ./gradlew :jasper-app:linuxX11TrayProbe -Pjasper.tray.packagedHome="$image" \
 --args='--gate bus --require-native --output build/tray-gate/task10-final-packaged-bus'
bash build/tray-gate/task10-packaged-runtime.sh xfce
bash build/tray-gate/task10-packaged-runtime.sh xfce task10-packaged-runtime-xfce-repeat
bash build/tray-gate/task10-packaged-runtime.sh lxde
python3 build/tray-gate/task10-audit.py
```

The headless package/notices/architecture command passed in 51 s; notices cover 37 shipped modules and the image has 41 runtime modules, 198 MB runtime, 29 MB default CDS. Final-source required suites pass 24 wire/status/transport tests and 57 native tests, zero failures/errors/skips. Exact packaged execution passes 20 transport watchdog cases and both filesystem/abstract private-bus forms, with exactly one junixsocket transport and one NOP logging provider. Native-load tracing records extraction/loading from the shipped junixsocket 2.10.1 jar. System `readelf -h -d -V`/`ldd` resolve `libxcb.so.1`, `libxcb-render.so.0`, `libXau.so.6`; no missing soname or new XCB shared library is shipped.

Executed host: Fedora Linux 44 (KDE Plasma Desktop Edition), aarch64; **isolated X11 only**, Xvfb 21.1.25-2.fc44, 1280x800x24 at JBR scale 1. Packaged Java is JBR 25.0.4.1+1-b635.75-jcef; D-Bus 1.16.2; libxcb 1.17.0-7.fc44, libXau 1.0.12-4.fc44, glibc 2.43-9.fc44. LXDE uses lxpanel 0.11.1^20260313git4dec3d0d-1.fc44/Openbox 3.6.1-30.fc44. XFCE uses panel 4.20.7-2.fc44/xfwm4 4.20.0-4.fc44 (`--compositor=off`) plus owned rootless xcompmgr 1.1.10-4.fc44. This records the host image, not an active Plasma result.

Both packaged panel/WM runtime rows pass the Task 9 composition sequence, real initial/fallback popup, stale-action rejection and prompt EDT close; screenshots were inspected. XFCE passed twice after a fixture-only input-readiness correction. The first XFCE replay confirmed replacement/recovery but clicked before the fallback popup opened; it is retained as failed evidence. The diagnostic replay observed installed/mapped/hostMapped, no modality/cleanup or retiring popup, server `IsViewable`, and a successful second native menu request. Install confirms XEmbed child admission while the panel can still map its ancestor; the probe now waits on a bounded server-observed `IsViewable` condition before physical input, rather than assuming install return means click readiness. No production delay, assertion removal or repeated click was added. The failure's precise transient timing is not independently reconstructed. Accepted runs retain before/after admission state. Final owned-process cleanup reports alive 0/private home removed; outer marker/runtime/start-time cleanup reports empty owned PID/mount lists and removed=true. Wrapper environment and Xvfb→private-bus order are unchanged from Task 9; no active desktop portal or `/run/user` mount was touched.

The Task 9 wire XML's uncaught `DBus-MethodCall-Receiver-4` `NotConnected` stack is a pinned-library reply-after-close diagnostic. A standalone characterization using only a deliberately closed private watcher reproduces that exact `ConnectionMethodInvocation.invokeMethodAndReply` stack, captures/asserts only the expected receiver failure, and uses no Jasper adapter. Five isolated unresponsive-watcher tests and final 20-test wire suite have no receiver diagnostic. The historical asynchronous stack cannot identify its original test method, so it is preserved without a stronger claim. No global suppression or production exception handler was added. Expected FINE `XCB canceled` during deliberate retirement and existing portal fallback/PipeWire, headless-splash/unavailable-command and ByteBuddy Unsafe warnings remain disclosed; output is not pristine.

Local final command:

```sh
./gradlew check :jasper-app:packageApp :jasper-app:verifyPackage -Pjasper.aot=false \
 -Pjasper.python=/Users/dustin/.local/share/uv/python/cpython-3.13.15-macos-aarch64-none/bin/python3.13
```

Passed in 12m 1s (160 tasks, 30 executed). XML reports **13,774 tests, 0 failures/errors, 100 skips; 13,674 executed** across 15 modules. Jasper-app reports 5128/88 skips; terminal 492/7; Editor 5304/4; Vault 221/1; the other modules have no skips. The 100 skips comprise 67 Linux-only tray/runtime cases on macOS plus 33 existing platform/environment/performance cases; the report preserves every skip identifier. The selected local toolchain is JBR 25.0.4.1+1-b583.48 at `/Users/dustin/Library/Java/JavaVirtualMachines/jbrsdk-25.0.4.1-osx-aarch64-b583.48/Contents/Home`, distinct from Fedora's packaged b635.75; Python is 3.13.15. Local image verification reports 41 runtime modules, 188 MB runtime, 29 MB default CDS, no AOT cache. The final opt-in probe source also passes `:jasper-app:compileTestJava :verifyApplicationArchitecture`; it changed after full-check compilation only for test-only native readiness diagnostics/oracle, and the final native probes above execute that exact source.

All 3965 source/build inputs match local/Fedora, manifest SHA256 `fe899e3d2bf7ab2fea4b9e095944ea0676e17da3e60522f054f78af138e3281f`. The package hash manifest SHA256 is `b307700cc78472b8c575c236ee0d9b76415d3a148870a5fd30c2d13e05c6a468`; packaged `java` SHA256 `32e1a562275e06d87169ef3d6b18b70ea5ef7139b19b014f065c1faa2b5c4c83`, `jasper-app.jar` SHA256 `6a5373f48a0ac7b4e3db8cf45c21f59e2c51dd08dd02b34be373f1311d803643`. Full individual jar/native hashes, library versions/ABI, exact classpaths/commands, XML, expected-omission failures, standalone disconnect characterization, wrapper/cleanup and accepted/excluded captures are retained under `.superpowers/sdd/2026-10-27-linux-residency-2-tray/task-10-evidence/`; the task report indexes them. Source hygiene scans 3078 Java files with no raw control/private-use/unpaired-surrogate characters; `git diff --check` passes. No SDK input changed, so no external-plugin/reference regeneration was required.

### Remaining release and user-session acceptance

| Release family | Executed evidence | Still pending |
|---|---|---|
| Ubuntu 22.04 x86_64/aarch64 | Required CI gates implemented | Actual native ABI/cancellation, both private-bus forms, exact packaged JBR/jars and dependency installation results on both runners |
| Ubuntu 24.04 LTS | None | GNOME Wayland/available X11, indicator enabled/disabled, full session checklist |
| Debian 12/13 | None | LXDE/MATE/XFCE X11 real sessions, owner/panel replacement, opaque/alpha composition, focus/menu |
| Fedora 44 aarch64 | Isolated LXDE/Openbox and XFCE/xfwm4 Xvfb gates above; earlier scale/artwork rows remain Task 7/8 evidence | Real GNOME/Plasma Wayland/available X11 and full session checklist |
| openSUSE Leap 16/current Tumbleweed | None | Plasma Wayland/X11 where supplied, RPM interoperability, owner/panel replacement |
| Arch dated snapshot | None | Record snapshot; LXQt/XFCE and available Wayland/X11 modern/legacy hosts |
| Physical monitors | None | Mixed 1x/2x, fractional scale, negative origins, device identity/edges/insets, no workspace/taskbar entry |

For every real desktop row, record distro point version/architecture, session, panel/extension version, JBR build and monitor transforms. Pending session checks: fresh-login background start with no shown rehearsal/shell; ordinary launch same PID; last-window close resident; all four actions once; vetoed Quit still live; accepted Quit ends workers/subscriptions without restart; opt-out survives upgrade/login; panel/applet restart and host enable/disable restore one icon; failed modern registration falls back; absent/lost bus recovers; no-host residency; sleep/wake per host family; logout ends the old PID and next login gets a new owner. A dead desktop leaving an orphan blocks session-lifetime acceptance and requires amendment; bus loss alone reconnects. The [GNOME indicator extension](https://extensions.gnome.org/extension/615/appindicator-support/) is a user acceptance prerequisite, not installed by the gate. Cooperative authority reads, bounded disposed AWT inventory residue and deferred Task 3 pixel coverage remain disclosed for final review. Task 10 independent review and final whole-branch review remain pending; no actual Jasper GUI/login session, AOT, benchmark, merge or push occurred.
