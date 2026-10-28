# Linux residency Plan 2 verification — 2026-10-27

**Task 1 Fedora aarch64 gate: PASS; per-task review pending.** Rulings 9–10 follow the user's
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
