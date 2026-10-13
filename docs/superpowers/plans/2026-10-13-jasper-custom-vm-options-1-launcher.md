# Custom VM Options, Plan 1: Launcher — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

Status: **Not started.** Written 2026-10-13 on `claude/custom-vm-options` (spec approved by the user the same day).

**Goal:** Packaged Jasper starts from its own build of jpackage's launcher, which merges the user's `jasper.vmoptions`
over the bundled JVM options and starts once more without them when the JVM rejects them.

**Architecture:** jpackage's launcher sources are vendored unmodified from the JBR tag except for marked hook lines.
A pure C++ core decides the options file's location, parses it and merges it IntelliJ's way; a thin wrapper applies
that to the launcher's cfg. A C exit guard re-spawns the launcher when the JVM was never created. Gradle compiles the
launcher with the host's compiler and hands it to jpackage through `-J--patch-module=jdk.jpackage=<dir>`. Native
tests, a guard test against the real libjli and a probe-image end-to-end test run in `check` on every OS.

**Tech Stack:** C++14 and C11 (clang on macOS, gcc/g++ on Linux, MSVC on Windows), Gradle Groovy scripts, jpackage
from JBR 25.0.4.1, Java 25 for probes.

**Spec:** [`docs/superpowers/specs/2026-10-13-jasper-custom-vm-options-design.md`](../specs/2026-10-13-jasper-custom-vm-options-design.md)
(sections 2–4, 6.1–6.3, 8, 9 and 11, plan 1).

## Global Constraints

- Upstream: JetBrains Runtime tag `jbr-release-25.0.4.1b583.48`, commit `868e6eaecc700d03b903fcb3b723d30c17d64de4`.
  Vendored files stay byte-identical except whole added lines containing `JASPER-HOOK`.
- File name `jasper.vmoptions`. Environment variables: `JASPER_VM_OPTIONS` (alternative file), `JASPER_HOME`,
  `JASPER_VM_OPTIONS_SKIP` (the guard sets it to `launch-failed`).
- Properties the launcher adds: `-Djasper.vmOptionsFile=<path>`, `-Djasper.vmOptionsDigest=<digest>` (only when user
  lines were applied), `-Djasper.vmOptionsFile.corrupted=true`, `-Djasper.vmOptionsSkipped=<reason>`.
- Digest: FNV-1a 64-bit (offset `cbf29ce484222325`, prime `100000001b3`) over each applied line's UTF-8 bytes followed
  by `\n`, written as 16 lowercase hex digits.
- Merge filters (IntelliJ): a user line starting `-XX:+` and ending `GC` drops bundled lines of that shape; a user
  `-XX:InitialRAMPercentage=` drops bundled `-Xms…`; a user `-XX:MinRAMPercentage=` or `-XX:MaxRAMPercentage=` drops
  bundled `-Xmx…`. User lines follow the bundled options.
- Bundled defaults added to `applicationDefaultJvmArgs`: `-Xmx2048m`, `-XX:+IgnoreUnrecognizedVMOptions`.
- C++14 / C11; macOS minimum 11.0; Windows static CRT (`/MT`), as jpackage builds them.
- Never launch the Jasper GUI, never run `trainAotCache`, `packageDist` or `-Pjasper.aot=true` (AGENTS.md). The probe
  images in this plan are headless and fine to run.
- Work in a worktree (superpowers:using-git-worktrees), never on `main`. Every commit message ends with:

  ```
  🤖 Generated with Claude Code at The Home Depot

  Co-Authored-By: Claude <noreply@anthropic.com>
  ```
- Source hygiene (AGENTS.md): no raw control, private-use or unpaired surrogate characters in source files; write
  escapes (`"\xEF\xBB\xBF"`, `'\u0000'`, `'J\u00fcrgen'`).

## Planned deviations from the spec

Recorded here and, when the plan completes, in `docs/STATUS.md`:

1. **Marker string.** Spec 6.1 names `jasper-launcher`. The guard and the core already reference the literal
   `JASPER_VM_OPTIONS_SKIP`, which survives dead-stripping, so packaging verification looks for it (and for
   `-Djasper.vmOptionsFile=` in Linux's `libapplauncher.so`) instead of adding an otherwise unused constant.
2. **Warning level.** Spec 6.2 puts all of `jasper/` under warnings-as-errors. `JasperVmOptions.cpp` includes upstream
   headers, so it compiles at upstream's level; the core, the guard and the tests are strict.
3. **Windows exit hook.** `jli.dll` and `jvm.dll` call the shared UCRT's `exit()`, which never runs a statically linked
   launcher's own `atexit` table, so on Windows the guard also registers through `ucrtbase.dll`'s `_crt_atexit`.
4. **Unknown means no.** The guard asks only the loaded libjvm (macOS's libjli exports a same-named wrapper that would
   load the default libjvm) and re-spawns only when it reports 0; with no libjvm loaded it does nothing, so an ordinary
   exit can never turn into a restart (spec 4.1, amended in Task 3's review).
5. **Benchmark isolation.** `StartupRuns` gives the launcher an empty options file whenever it isolates the home, for
   the same reason `trainAotCache` does (spec 6.3).
6. **Byte order mark.** A UTF-8 BOM at the start of the file is skipped (the spec is silent; Notepad writes one).
7. **Recovery scope.** HotSpot's initialization failures (`-Xms` above `-Xmx`, a missing agent, an unusable CDS archive)
   end with `_exit`, which runs no exit handler, so the launcher cannot recover from them (spec 4.5, added in Task 3's
   review); plan 2's System Settings check reports them before a restart.
8. **Who removes the skip variable.** The guard's `jasperGuardBeforeLaunch` removes `JASPER_VM_OPTIONS_SKIP` just
   before `JLI_Launch`, in the process that starts the JVM; the wrapper only reads it (the core's `Plan` has no
   `removeSkipVariable`). On Linux the cfg is read in a forked child and on Windows possibly in a restart wrapper, so a
   removal there never reached the JVM (Task 4's review). The options file must also be a regular file off Windows
   (`S_ISREG`), and the Windows link passes `/MANIFEST:NO` because `java.manifest` is embedded with `mt`.

## Review Focus

Inputs the spec implies but no other test pins, most likely to bite first. Each has its test in the owning task:

1. A file saved with a UTF-8 byte order mark (Windows Notepad): the BOM is ignored and the options apply — Task 2.
2. A home path with spaces and non-ASCII characters (`J\u00fcrgen home`): the file is found and read — Task 5.
3. Application arguments with spaces (the login item's `--background`, file paths) survive the fallback re-spawn —
   Task 5.
4. `jasper.vmoptions` is a directory: treated as no file, Jasper starts with the bundled options — Task 5.
5. Windows' exit path (UCRT `exit()` versus the launcher's static CRT): the guard still fires, in both the console and
   the windowed launcher — Tasks 3 and 5, on the Windows CI runner.

## File structure

| Path | Responsibility |
|---|---|
| `packaging/launcher/README.md` | Provenance, hooks, build, tests, re-porting. |
| `packaging/launcher/sha256sums.py` | Regenerates `upstream/SHA256SUMS`. |
| `packaging/launcher/upstream/**` | Vendored JBR launcher sources, `LICENSE`, `SHA256SUMS`. |
| `packaging/launcher/jasper/JasperVmOptionsCore.{h,cpp}` | Pure logic: location, parsing, merge, digest, plan. |
| `packaging/launcher/jasper/JasperVmOptions.{h,cpp}` | Wrapper: environment and file access, rewrites the cfg. |
| `packaging/launcher/jasper/jasper_launch_guard.{h,c}` | The exit guard and re-spawn. |
| `packaging/launcher/test/vmoptions_core_test.cpp` | Native tests of the core. |
| `packaging/launcher/test/digest-vectors.txt` | Digest vectors shared with plan 2's Java tests. |
| `packaging/launcher/test/guard_harness.c`, `GuardProbe.java` | Guard test against the real libjli. |
| `packaging/launcher/test/LauncherProbe.java` | Probe app for the end-to-end image test. |
| `gradle/launcher.gradle` | Toolchain, launcher build and all launcher verification tasks. |
| `gradle/packaging.gradle`, `gradle/packaging-support.gradle` (+ `-test`) | Use the launcher; training isolation. |
| `gradle/mac-launcher-identity.gradle` | Comment: the identity seed is the compiled launcher. |
| `jasper-app/build.gradle.kts` | Bundled defaults. |
| `jasper-app/src/main/java/dev/jasper/app/benchmark/StartupRuns.java` (+ test) | Benchmark isolation. |
| Docs | `docs/packaging.md`, `docs/app-maintenance.md`, `AGENTS.md`, `docs/STATUS.md`, the spec's status line. |

`gradle/launcher.gradle` grows task by task. Groovy resolves a script's local variables lexically, so each task appends
its block **at the end of the file** and adds any imports **at the top**; later blocks use earlier ones.

---

### Task 1: Vendor jpackage's launcher sources

**Files:**
- Create: `packaging/launcher/upstream/**` (71 files), `packaging/launcher/upstream/SHA256SUMS`
- Create: `packaging/launcher/sha256sums.py`, `packaging/launcher/README.md`, `gradle/launcher.gradle`
- Modify: `gradle/packaging.gradle` (apply the new script)

**Interfaces:**
- Produces (in `gradle/launcher.gradle`): locals `launcherSource` (File), `upstreamRoot` (File), `launcherBuild`
  (Provider<Directory>, `build/launcher`), `launcherHookedFiles` (Set<String>, empty until Task 4); task
  `verifyLauncherSources` (in `check`).

- [ ] **Step 1: Write the verification task**

Create `gradle/launcher.gradle`:

```groovy
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.security.MessageDigest

// Jasper's build of jpackage's native application launcher (packaging/launcher/README.md), applied by
// gradle/packaging.gradle. Blocks are in dependency order: later tasks use the helpers defined above them.

def launcherSource = rootProject.file('packaging/launcher')
def upstreamRoot = new File(launcherSource, 'upstream')
def launcherBuild = layout.buildDirectory.dir('launcher')
// Vendored files that carry Jasper's hook lines (each such line contains JASPER-HOOK); every other file is upstream's.
def launcherHookedFiles = [] as Set<String>

// A vendored file as upstream ships it: Jasper's hook lines removed, CRLF read as LF. ISO-8859-1 keeps every byte.
def upstreamText = { File file ->
    new String(Files.readAllBytes(file.toPath()), StandardCharsets.ISO_8859_1).replace('\r\n', '\n')
        .replaceAll('(?m)^[^\n]*JASPER-HOOK[^\n]*\n', '')
}

def verifyLauncherSources = tasks.register('verifyLauncherSources') {
    group = 'verification'
    description = 'Checks packaging/launcher/upstream against SHA256SUMS: upstream bytes apart from JASPER-HOOK lines.'
    inputs.dir(upstreamRoot).withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.file(launcherBuild.map { it.file('sources-verified') })
    doLast {
        File sums = new File(upstreamRoot, 'SHA256SUMS')
        if (!sums.isFile()) throw new GradleException("verifyLauncherSources: ${sums} is missing")
        Map<String, String> expected = [:]
        sums.readLines('UTF-8').findAll { it.trim() && !it.startsWith('#') }.each { String line ->
            def match = line =~ /^([0-9a-f]{64})  (\S.*)$/
            if (!match.matches()) throw new GradleException("verifyLauncherSources: malformed line in ${sums}: ${line}")
            expected[match.group(2)] = match.group(1)
        }
        Set<String> present = [] as Set
        upstreamRoot.eachFileRecurse(groovy.io.FileType.FILES) { File file ->
            String path = upstreamRoot.toPath().relativize(file.toPath()).toString().replace('\\', '/')
            if (path != 'SHA256SUMS') present << path
        }
        List<String> problems = []
        (expected.keySet() - present).sort().each { problems << "missing: ${it}".toString() }
        (present - expected.keySet()).sort().each { problems << "not in SHA256SUMS: ${it}".toString() }
        Set<String> hooked = [] as Set
        expected.keySet().intersect(present).sort().each { String path ->
            File file = new File(upstreamRoot, path)
            if (new String(Files.readAllBytes(file.toPath()), StandardCharsets.ISO_8859_1).contains('JASPER-HOOK')) hooked << path
            String actual = MessageDigest.getInstance('SHA-256')
                .digest(upstreamText(file).getBytes(StandardCharsets.ISO_8859_1)).encodeHex().toString()
            if (actual != expected[path]) problems << "differs from upstream beyond JASPER-HOOK lines: ${path}".toString()
        }
        if (hooked != launcherHookedFiles) {
            problems << "files with JASPER-HOOK lines: ${hooked.sort()}; expected ${launcherHookedFiles.sort()}".toString()
        }
        if (problems) throw new GradleException('verifyLauncherSources:\n' + problems.join('\n'))
        File marker = launcherBuild.get().file('sources-verified').asFile
        marker.parentFile.mkdirs()
        marker.text = "${expected.size()} files\n"
    }
}
tasks.named('check') { dependsOn(verifyLauncherSources) }
```

In `gradle/packaging.gradle`, after the line `apply from: rootProject.file('gradle/packaging-support-test.gradle')`, add:

```groovy
apply from: rootProject.file('gradle/launcher.gradle')
```

- [ ] **Step 2: Run it to see it fail**

Run: `./gradlew :jasper-app:verifyLauncherSources`
Expected: FAIL — the input directory `packaging/launcher/upstream` does not exist.

- [ ] **Step 3: Vendor the sources and write the checksum script**

Create `packaging/launcher/sha256sums.py`:

```python
#!/usr/bin/env python3
"""Writes packaging/launcher/upstream/SHA256SUMS: every vendored file as upstream ships it (README.md)."""
import hashlib
import pathlib
import re

root = pathlib.Path(__file__).resolve().parent / 'upstream'
lines = ["# SHA-256 of each vendored file with LF line endings and without JASPER-HOOK lines. See ../README.md."]
for path in sorted(p for p in root.rglob('*') if p.is_file() and p.name != 'SHA256SUMS'):
    data = path.read_bytes().replace(b'\r\n', b'\n')
    data = re.sub(rb'(?m)^[^\n]*JASPER-HOOK[^\n]*\n', b'', data)
    lines.append(f'{hashlib.sha256(data).hexdigest()}  {path.relative_to(root).as_posix()}')
(root / 'SHA256SUMS').write_text('\n'.join(lines) + '\n', encoding='utf-8')
```

Then run from the repository root:

```bash
SRC=$(mktemp -d)
git clone --quiet --depth 1 --branch jbr-release-25.0.4.1b583.48 --filter=blob:none --sparse \
    https://github.com/JetBrains/JetBrainsRuntime.git "$SRC"
git -C "$SRC" sparse-checkout set --no-cone /LICENSE \
    /src/jdk.jpackage/share/native/applauncher/ /src/jdk.jpackage/share/native/common/ \
    /src/jdk.jpackage/unix/native/common/ /src/jdk.jpackage/macosx/native/applauncher/ \
    /src/jdk.jpackage/macosx/native/common/ /src/jdk.jpackage/linux/native/applauncher/ \
    /src/jdk.jpackage/linux/native/common/ /src/jdk.jpackage/linux/native/libapplauncher/ \
    /src/jdk.jpackage/windows/native/applauncher/ /src/jdk.jpackage/windows/native/common/ \
    /src/java.base/windows/native/common/version.rc /src/java.base/windows/native/launcher/java.manifest
test "$(git -C "$SRC" rev-parse HEAD)" = 868e6eaecc700d03b903fcb3b723d30c17d64de4 && echo "commit ok"
mkdir -p packaging/launcher/upstream
(cd "$SRC" && find LICENSE src -type f -print0 | xargs -0 tar cf -) | tar xf - -C packaging/launcher/upstream
find packaging/launcher/upstream -type f | wc -l
python3 packaging/launcher/sha256sums.py
```

Expected: `commit ok`, then `71` (68 launcher sources, `version.rc`, `java.manifest`, `LICENSE`).

Create `packaging/launcher/README.md`:

~~~markdown
# Jasper's jpackage launcher

Packaged Jasper starts from jpackage's native application launcher. Jasper builds that launcher itself from the
JetBrains Runtime's sources, with one addition: it merges the user's `jasper.vmoptions` over the bundled JVM options,
and starts once more without them when the JVM rejects them. The design is the spec
`docs/superpowers/specs/2026-10-13-jasper-custom-vm-options-design.md`.

## Upstream sources

`upstream/` holds the launcher sources of JetBrains Runtime tag `jbr-release-25.0.4.1b583.48` (commit
`868e6eaecc700d03b903fcb3b723d30c17d64de4`) at their upstream paths, and that repository's `LICENSE`. The vendored files
are GPL version 2 with the Classpath Exception, as each header says; Jasper's changes to them are the lines marked
`JASPER-HOOK`, published here.

`upstream/SHA256SUMS` records every vendored file as upstream ships it (LF line endings, no `JASPER-HOOK` lines).
`verifyLauncherSources`, part of `check`, fails when a file is missing, unlisted or different in any other way, and when
the files carrying hook lines are not the ones `gradle/launcher.gradle` names.

## Updating to a new JBR

Vendor the new tag (shown with the current one), check the commit, and regenerate the checksums:

```bash
SRC=$(mktemp -d)
git clone --quiet --depth 1 --branch jbr-release-25.0.4.1b583.48 --filter=blob:none --sparse \
    https://github.com/JetBrains/JetBrainsRuntime.git "$SRC"
git -C "$SRC" sparse-checkout set --no-cone /LICENSE \
    /src/jdk.jpackage/share/native/applauncher/ /src/jdk.jpackage/share/native/common/ \
    /src/jdk.jpackage/unix/native/common/ /src/jdk.jpackage/macosx/native/applauncher/ \
    /src/jdk.jpackage/macosx/native/common/ /src/jdk.jpackage/linux/native/applauncher/ \
    /src/jdk.jpackage/linux/native/common/ /src/jdk.jpackage/linux/native/libapplauncher/ \
    /src/jdk.jpackage/windows/native/applauncher/ /src/jdk.jpackage/windows/native/common/ \
    /src/java.base/windows/native/common/version.rc /src/java.base/windows/native/launcher/java.manifest
git -C "$SRC" rev-parse HEAD
rm -rf packaging/launcher/upstream && mkdir -p packaging/launcher/upstream
(cd "$SRC" && find LICENSE src -type f -print0 | xargs -0 tar cf -) | tar xf - -C packaging/launcher/upstream
python3 packaging/launcher/sha256sums.py
```

Then re-apply the `JASPER-HOOK` lines, update the tag and commit in this file, and run `./gradlew :jasper-app:check`.
~~~

- [ ] **Step 4: Run the verification to see it pass**

Run: `./gradlew :jasper-app:verifyLauncherSources`
Expected: BUILD SUCCESSFUL; `jasper-app/build/launcher/sources-verified` reads `71 files`.

- [ ] **Step 5: Prove it catches a change, then restore**

```bash
printf ' ' >> packaging/launcher/upstream/src/jdk.jpackage/share/native/applauncher/CfgFile.cpp
./gradlew :jasper-app:verifyLauncherSources
git checkout -- packaging/launcher/upstream/src/jdk.jpackage/share/native/applauncher/CfgFile.cpp 2>/dev/null || \
    python3 - <<'PY'
import pathlib
p = pathlib.Path('packaging/launcher/upstream/src/jdk.jpackage/share/native/applauncher/CfgFile.cpp')
p.write_bytes(p.read_bytes()[:-1])
PY
./gradlew :jasper-app:verifyLauncherSources
```

Expected: the first run FAILS with `differs from upstream beyond JASPER-HOOK lines: src/jdk.jpackage/share/native/applauncher/CfgFile.cpp`;
the second run passes. (Before the first commit `git checkout` has nothing to restore, so the Python fallback removes
the added byte.)

- [ ] **Step 6: Commit**

```bash
git add packaging/launcher gradle/launcher.gradle gradle/packaging.gradle
git commit -F - <<'EOF'
Launcher: vendor jpackage's launcher sources from JBR 25.0.4.1

packaging/launcher/upstream holds the launcher sources of tag
jbr-release-25.0.4.1b583.48 with their LICENSE; verifyLauncherSources (in
check) holds them to SHA256SUMS apart from Jasper's JASPER-HOOK lines.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 2: The options logic (pure C++) and the host toolchain

**Files:**
- Create: `packaging/launcher/jasper/JasperVmOptionsCore.h`, `packaging/launcher/jasper/JasperVmOptionsCore.cpp`
- Create: `packaging/launcher/test/vmoptions_core_test.cpp`, `packaging/launcher/test/digest-vectors.txt`
- Modify: `gradle/launcher.gradle` (imports at the top; block at the end)

**Interfaces:**
- Consumes: Task 1's `launcherSource`, `launcherBuild`.
- Produces (C++, namespace `jasper::vmoptions`): `enum class Os { Mac, Linux, Windows }`;
  `typedef std::map<std::string, std::string> Environment`; constants `FILE_NAME`, `VM_OPTIONS_VARIABLE`,
  `SKIP_VARIABLE`, `HOME_VARIABLE`; `struct UserFile { std::vector<std::string> lines; bool corrupted; }`;
  `std::string userFilePath(Os, const Environment&, const std::string& workingDirectory, const std::function<bool(const std::string&)>& isRegularFile)`;
  `UserFile parse(const std::string& bytes)`;
  `std::vector<std::string> merge(const std::vector<std::string>& bundled, const std::vector<std::string>& user)`;
  `std::string digest(const std::vector<std::string>& lines)`;
  `struct Plan { std::vector<std::string> javaOptions; bool removeSkipVariable; }`;
  `Plan plan(const std::vector<std::string>& bundled, const std::string& path, const UserFile* file, const Environment& env)`.
- Produces (Gradle locals): `jasperNative`, `launcherTestSource` (File); `launcherSdk` (Provider<File>, the toolchain
  JDK home); `launcherHost()` → `[os: 'macos'|'linux'|'windows', architecture: 'aarch64'|'x64']`;
  `runLauncherTool(List<String> command, Map<String,String> environment, File directory, String purpose, String hint = null)` → output String;
  `msvc()` → environment Map; `msvcTool(String exe)` → path; `launcherToolchainHint(String os)`;
  `nativeBuild(Map spec)` → File (spec keys: `kind` `'executable'|'shared'`, `output`, `work`, `sources`,
  `includes`, `defines`, `strict`, `exportAll`, `libraries`, `rpaths`, `subsystem`, `resources`);
  task `testLauncherCore` (in `check`).

- [ ] **Step 1: Write the failing test**

Create `packaging/launcher/test/digest-vectors.txt` (UTF-8):

```text
# FNV-1a 64 digests of option lines (packaging/launcher/README.md): a vector's lines, then "= <digest>".
# Read by the launcher's native tests and by the application's tests; keep both implementations in step.
= cbf29ce484222325
-Xmx2048m
= c758526d21c369d0
-Xmx512m
-Dfoo=a b
= 67c591cacf95837b
-Dname=Jürgen
= 1faff028097f1b87
-XX:+UseZGC
-Xmx3q
= 1514f6a9887c4cca
```

Create `packaging/launcher/test/vmoptions_core_test.cpp`:

```cpp
// Native tests of JasperVmOptionsCore (packaging/launcher/README.md). Usage: vmoptions-core-test <digest-vectors.txt>
#include "JasperVmOptionsCore.h"

#include <algorithm>
#include <fstream>
#include <iostream>
#include <string>
#include <vector>

namespace vm = jasper::vmoptions;
using vm::Environment;
using vm::Os;
using vm::Plan;
using vm::UserFile;

namespace {

int failures = 0;
int checks = 0;

void check(bool condition, const std::string& what) {
    ++checks;
    if (!condition) {
        ++failures;
        std::cerr << "FAIL: " << what << "\n";
    }
}

std::string show(const std::vector<std::string>& values) {
    std::string text = "[";
    for (std::vector<std::string>::size_type i = 0; i < values.size(); ++i) {
        text += (i == 0 ? "" : " | ") + values[i];
    }
    return text + "]";
}

void expectLines(const std::vector<std::string>& actual, const std::vector<std::string>& expected,
                 const std::string& what) {
    check(actual == expected, what + ": got " + show(actual) + ", expected " + show(expected));
}

void expectText(const std::string& actual, const std::string& expected, const std::string& what) {
    check(actual == expected, what + ": got \"" + actual + "\", expected \"" + expected + "\"");
}

std::function<bool(const std::string&)> existing(const std::vector<std::string>& files) {
    return [files](const std::string& path) { return std::find(files.begin(), files.end(), path) != files.end(); };
}

void location() {
    const auto none = existing({});
    expectText(vm::userFilePath(Os::Mac, {{"HOME", "/Users/a"}}, "/", none),
        "/Users/a/.config/jasper/jasper.vmoptions", "macOS default");
    expectText(vm::userFilePath(Os::Mac, {{"HOME", "/Users/a"}, {"XDG_CONFIG_HOME", "/xdg"}}, "/", none),
        "/Users/a/.config/jasper/jasper.vmoptions", "macOS ignores XDG_CONFIG_HOME");
    expectText(vm::userFilePath(Os::Linux, {{"HOME", "/home/a"}, {"XDG_CONFIG_HOME", "/xdg"}}, "/", none),
        "/xdg/jasper/jasper.vmoptions", "Linux absolute XDG_CONFIG_HOME");
    expectText(vm::userFilePath(Os::Linux, {{"HOME", "/home/a"}, {"XDG_CONFIG_HOME", "relative"}}, "/", none),
        "/home/a/.config/jasper/jasper.vmoptions", "Linux ignores a relative XDG_CONFIG_HOME");
    expectText(vm::userFilePath(Os::Windows, {{"APPDATA", "C:\\Users\\a\\AppData\\Roaming"}}, "C:\\", none),
        "C:\\Users\\a\\AppData\\Roaming\\jasper\\jasper.vmoptions", "Windows APPDATA");
    expectText(vm::userFilePath(Os::Windows, {{"APPDATA", "relative"}, {"USERPROFILE", "C:\\Users\\a"}}, "C:\\", none),
        "C:\\Users\\a\\AppData\\Roaming\\jasper\\jasper.vmoptions", "Windows falls back to USERPROFILE");
    expectText(vm::userFilePath(Os::Windows, {}, "C:\\", none), "", "Windows without APPDATA or USERPROFILE");
    expectText(vm::userFilePath(Os::Mac, {}, "/", none), "", "macOS without HOME");
    expectText(vm::userFilePath(Os::Mac, {{"JASPER_HOME", " /opt/jh "}, {"HOME", "/Users/a"}}, "/", none),
        "/opt/jh/jasper.vmoptions", "JASPER_HOME, trimmed");
    expectText(vm::userFilePath(Os::Linux, {{"JASPER_HOME", "/opt/jh/"}}, "/", none),
        "/opt/jh/jasper.vmoptions", "JASPER_HOME with a trailing separator");
    expectText(vm::userFilePath(Os::Linux, {{"JASPER_HOME", "jh"}}, "/work", none),
        "/work/jh/jasper.vmoptions", "a relative JASPER_HOME resolves from the working directory");
    expectText(vm::userFilePath(Os::Windows, {{"JASPER_HOME", "jh"}}, "C:\\work", none),
        "C:\\work\\jh\\jasper.vmoptions", "a relative JASPER_HOME on Windows");
    expectText(vm::userFilePath(Os::Mac, {{"JASPER_HOME", "   "}, {"HOME", "/Users/a"}}, "/", none),
        "/Users/a/.config/jasper/jasper.vmoptions", "a blank JASPER_HOME is ignored");
    const Environment chosen = {{"JASPER_VM_OPTIONS", "/tmp/mine.vmoptions"}, {"JASPER_HOME", "/opt/jh"}};
    expectText(vm::userFilePath(Os::Mac, chosen, "/", existing({"/tmp/mine.vmoptions"})),
        "/tmp/mine.vmoptions", "JASPER_VM_OPTIONS naming a file");
    expectText(vm::userFilePath(Os::Mac, chosen, "/", none),
        "/opt/jh/jasper.vmoptions", "JASPER_VM_OPTIONS naming no file falls through");
    expectText(vm::userFilePath(Os::Mac, {{"JASPER_VM_OPTIONS", "  "}, {"HOME", "/Users/a"}}, "/", existing({"  "})),
        "/Users/a/.config/jasper/jasper.vmoptions", "a blank JASPER_VM_OPTIONS is ignored");
}

void parsing() {
    const UserFile mixed = vm::parse("# heap\r\n\r\n  -Xmx1g  \r\n\t-Dfoo=a b\t\n#-Xmx2g\n-XX:+UseZGC");
    expectLines(mixed.lines, {"-Xmx1g", "-Dfoo=a b", "-XX:+UseZGC"}, "comments, blank lines, trimming, CRLF");
    check(!mixed.corrupted, "a clean file is not corrupted");
    expectLines(vm::parse("-Dcolor=#fff\n").lines, {"-Dcolor=#fff"}, "only whole-line comments");
    expectLines(vm::parse("\xEF\xBB\xBF-Xmx1g\n").lines, {"-Xmx1g"}, "a UTF-8 byte order mark is ignored");
    expectLines(vm::parse("-Dname=J\xC3\xBCrgen\n").lines, {"-Dname=J\xC3\xBCrgen"}, "UTF-8 text");
    expectLines(vm::parse("").lines, {}, "an empty file");
    expectLines(vm::parse("# only\n\n   \n").lines, {}, "only comments and blank lines");
    const char nulBytes[] = "-Xmx1g\n\0-Xms1g\n";
    const UserFile nul = vm::parse(std::string(nulBytes, sizeof(nulBytes) - 1));
    check(nul.corrupted && nul.lines.empty(), "a NUL character corrupts the file");
    check(vm::parse("-Dname=\xC3\x28\n").corrupted, "invalid UTF-8 corrupts the file");
    check(vm::parse("-D=\xC0\xAF\n").corrupted, "overlong UTF-8 corrupts the file");
    check(vm::parse("-D=\xED\xA0\x80\n").corrupted, "a UTF-8 surrogate corrupts the file");
    check(vm::parse("-D=\xE2\x82").corrupted, "truncated UTF-8 corrupts the file");
}

void merging() {
    expectLines(vm::merge({"-a", "-b"}, {"-c"}), {"-a", "-b", "-c"}, "user lines follow the bundled options");
    expectLines(vm::merge({"-XX:+UseG1GC", "-Xmx2048m"}, {"-XX:+UseZGC"}), {"-Xmx2048m", "-XX:+UseZGC"},
        "a user collector removes the bundled collector");
    expectLines(vm::merge({"-XX:+UseG1GC"}, {"-XX:+DisableExplicitGC"}), {"-XX:+DisableExplicitGC"},
        "IntelliJ's text match: any -XX:+...GC line counts as a collector");
    expectLines(vm::merge({"-XX:+UseG1GC"}, {"-XX:-UseG1GC"}), {"-XX:+UseG1GC", "-XX:-UseG1GC"},
        "a disabled collector is not a collector choice");
    expectLines(vm::merge({"-XX:+UseG1GC"}, {"-Xmx1g"}), {"-XX:+UseG1GC", "-Xmx1g"},
        "without a user collector the bundled one stays");
    expectLines(vm::merge({"-Xmx2048m", "-Xms128m"}, {"-XX:MaxRAMPercentage=50"}),
        {"-Xms128m", "-XX:MaxRAMPercentage=50"}, "MaxRAMPercentage removes the bundled -Xmx");
    expectLines(vm::merge({"-Xmx2048m"}, {"-XX:MinRAMPercentage=5"}), {"-XX:MinRAMPercentage=5"},
        "MinRAMPercentage removes the bundled -Xmx");
    expectLines(vm::merge({"-Xmx2048m", "-Xms128m"}, {"-XX:InitialRAMPercentage=5"}),
        {"-Xmx2048m", "-XX:InitialRAMPercentage=5"}, "InitialRAMPercentage removes the bundled -Xms");
    expectLines(vm::merge({"-Xmx2048m"}, {"-Xmx4g"}), {"-Xmx2048m", "-Xmx4g"}, "a user -Xmx wins by coming last");
}

void digests(const char* vectorsPath) {
    std::ifstream input(vectorsPath, std::ios::binary);
    check(static_cast<bool>(input), std::string("cannot read ") + vectorsPath);
    std::vector<std::string> lines;
    std::string line;
    int vectors = 0;
    while (std::getline(input, line)) {
        if (!line.empty() && line[line.size() - 1] == '\r') {
            line.erase(line.size() - 1);
        }
        if (line.empty() || line[0] == '#') {
            continue;
        }
        if (line.compare(0, 2, "= ") == 0) {
            expectText(vm::digest(lines), line.substr(2), "digest of " + show(lines));
            lines.clear();
            ++vectors;
        } else {
            lines.push_back(line);
        }
    }
    check(vectors >= 5, "the vectors file holds at least five vectors");
}

void planning() {
    const std::vector<std::string> bundled = {"-Xmx2048m", "-XX:+UseG1GC"};
    UserFile lines;
    lines.lines = {"-Xmx4g", "-XX:+UseZGC"};
    UserFile empty;
    UserFile corrupted;
    corrupted.corrupted = true;
    const std::string path = "/h/jasper.vmoptions";
    const std::string file = "-Djasper.vmOptionsFile=/h/jasper.vmoptions";

    Plan p = vm::plan(bundled, "", nullptr, {});
    expectLines(p.javaOptions, bundled, "no location: only the bundled options");
    check(!p.removeSkipVariable, "nothing to remove without the skip variable");
    expectLines(vm::plan(bundled, path, nullptr, {}).javaOptions, {"-Xmx2048m", "-XX:+UseG1GC", file},
        "a missing file is still reported");
    expectLines(vm::plan(bundled, path, &empty, {}).javaOptions, {"-Xmx2048m", "-XX:+UseG1GC", file},
        "a file without options adds no digest");
    expectLines(vm::plan(bundled, path, &lines, {}).javaOptions,
        {"-Xmx2048m", "-Xmx4g", "-XX:+UseZGC", file, "-Djasper.vmOptionsDigest=" + vm::digest(lines.lines)},
        "the applied lines, the file and their digest");
    expectLines(vm::plan(bundled, path, &corrupted, {}).javaOptions,
        {"-Xmx2048m", "-XX:+UseG1GC", file, "-Djasper.vmOptionsFile.corrupted=true"}, "a corrupted file is reported");
    p = vm::plan(bundled, path, &lines, {{"JASPER_VM_OPTIONS_SKIP", " launch-failed "}});
    expectLines(p.javaOptions, {"-Xmx2048m", "-XX:+UseG1GC", file, "-Djasper.vmOptionsSkipped=launch-failed"},
        "the skip variable skips the file");
    check(p.removeSkipVariable, "the skip variable is removed before the JVM starts");
    p = vm::plan(bundled, path, &lines, {{"JASPER_VM_OPTIONS_SKIP", "  "}});
    check(p.removeSkipVariable, "a blank skip variable is removed too");
    check(p.javaOptions.back() == "-Djasper.vmOptionsDigest=" + vm::digest(lines.lines),
        "a blank skip variable skips nothing");
    expectLines(vm::plan(bundled, "", &lines, {{"JASPER_VM_OPTIONS_SKIP", "manual"}}).javaOptions,
        {"-Xmx2048m", "-XX:+UseG1GC", "-Djasper.vmOptionsSkipped=manual"}, "skipping without a location");
    expectLines(vm::plan(bundled, "", &lines, {}).javaOptions, bundled, "no location applies no lines");
}

} // namespace

int main(int argc, char** argv) {
    if (argc != 2) {
        std::cerr << "usage: vmoptions-core-test <digest-vectors.txt>\n";
        return 64;
    }
    location();
    parsing();
    merging();
    digests(argv[1]);
    planning();
    std::cout << checks << " checks, " << failures << " failures\n";
    return failures == 0 ? 0 : 1;
}
```

Create the header `packaging/launcher/jasper/JasperVmOptionsCore.h` now (the test needs it to compile):

```cpp
// The launcher's handling of the user's jasper.vmoptions, as pure functions over UTF-8 strings
// (packaging/launcher/README.md; spec sections 3 and 4.2). No upstream or platform code.
#ifndef JasperVmOptionsCore_h
#define JasperVmOptionsCore_h

#include <functional>
#include <map>
#include <string>
#include <vector>

namespace jasper {
namespace vmoptions {

enum class Os { Mac, Linux, Windows };

/** Environment variables by name, UTF-8; a missing key is an unset variable. */
typedef std::map<std::string, std::string> Environment;

const char* const FILE_NAME = "jasper.vmoptions";
const char* const VM_OPTIONS_VARIABLE = "JASPER_VM_OPTIONS";
const char* const SKIP_VARIABLE = "JASPER_VM_OPTIONS_SKIP";
const char* const HOME_VARIABLE = "JASPER_HOME";

/** The user's options file, parsed. */
struct UserFile {
    std::vector<std::string> lines;
    bool corrupted = false;
};

/**
 * The file the launcher reads: JASPER_VM_OPTIONS when it names a regular file, else <JASPER_HOME>/jasper.vmoptions,
 * else the OS default in the Jasper home. Empty when no location can be determined.
 */
std::string userFilePath(Os os, const Environment& env, const std::string& workingDirectory,
                         const std::function<bool(const std::string&)>& isRegularFile);

/** One option per line, trimmed; blank and '#' lines skipped; a leading BOM skipped; NUL or bad UTF-8 corrupts. */
UserFile parse(const std::string& bytes);

/** The bundled options without those the user's lines replace, then the user's lines. */
std::vector<std::string> merge(const std::vector<std::string>& bundled, const std::vector<std::string>& user);

/** FNV-1a 64 over each line's bytes followed by '\n', as 16 lowercase hex digits. */
std::string digest(const std::vector<std::string>& lines);

/** The JVM options to launch with, and whether to remove JASPER_VM_OPTIONS_SKIP from the environment first. */
struct Plan {
    std::vector<std::string> javaOptions;
    bool removeSkipVariable = false;
};

/** The whole decision. file is null when the file does not exist or cannot be read. */
Plan plan(const std::vector<std::string>& bundled, const std::string& path, const UserFile* file,
          const Environment& env);

} // namespace vmoptions
} // namespace jasper

#endif // JasperVmOptionsCore_h
```

Append to the **top** of `gradle/launcher.gradle`, after the existing imports:

```groovy
import java.util.Locale
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.jvm.toolchain.JavaToolchainService
```

Append to the **end** of `gradle/launcher.gradle`:

```groovy
// ---- The host's C/C++ toolchain ----

def jasperNative = new File(launcherSource, 'jasper')
def launcherTestSource = new File(launcherSource, 'test')
def launcherToolchains = extensions.getByType(JavaToolchainService)
def launcherJava = extensions.getByType(JavaPluginExtension)
def launcherSdk = launcherToolchains.launcherFor(launcherJava.toolchain).map { it.metadata.installationPath.asFile }

def launcherHost = { ->
    String os = System.getProperty('os.name').toLowerCase(Locale.ROOT)
    String architecture = System.getProperty('os.arch').toLowerCase(Locale.ROOT)
    [os: os.startsWith('mac') ? 'macos' : (os.startsWith('windows') ? 'windows' : 'linux'),
     architecture: architecture in ['aarch64', 'arm64'] ? 'aarch64' : 'x64']
}

def launcherToolchainHint = { String os ->
    switch (os) {
        case 'macos': return 'Install the Xcode command line tools: xcode-select --install'
        case 'linux': return 'Install gcc and g++ (Debian/Ubuntu: sudo apt-get install build-essential; Fedora: sudo dnf install gcc gcc-c++)'
        default: return "Install Visual Studio or its Build Tools with the 'Desktop development with C++' workload"
    }
}

// Runs a tool and returns its output; throws with the command and output when it fails or cannot start.
def runLauncherTool = { List<String> command, Map<String, String> environment, File directory, String purpose, String hint = null ->
    def builder = new ProcessBuilder(command.collect { it.toString() }).redirectErrorStream(true)
    if (directory != null) builder.directory(directory)
    if (environment != null) {
        builder.environment().clear()
        builder.environment().putAll(environment)
    }
    Process process
    try {
        process = builder.start()
    } catch (IOException failure) {
        throw new GradleException("${purpose}: cannot run ${command[0]}${hint ? '. ' + hint : ''}", failure)
    }
    String output = new String(process.inputStream.readAllBytes(), StandardCharsets.UTF_8)
    int exit = process.waitFor()
    if (exit != 0) throw new GradleException("${purpose} failed with exit code ${exit}\n${command.join(' ')}\n${output}")
    output
}

// MSVC's environment for this host's architecture, from vcvarsall.bat as vswhere finds it; read once per build.
def msvcCache = [:]
def msvc = { ->
    if (msvcCache.environment != null) return msvcCache.environment
    String hint = launcherToolchainHint('windows')
    String programFiles = System.getenv('ProgramFiles(x86)') ?: 'C:\\Program Files (x86)'
    File vswhere = new File(programFiles, 'Microsoft Visual Studio\\Installer\\vswhere.exe')
    if (!vswhere.isFile()) throw new GradleException("Building Jasper's launcher needs MSVC; ${vswhere} was not found. ${hint}")
    boolean arm = launcherHost().architecture == 'aarch64'
    String component = arm ? 'Microsoft.VisualStudio.Component.VC.Tools.ARM64' : 'Microsoft.VisualStudio.Component.VC.Tools.x86.x64'
    String installation = runLauncherTool([vswhere.absolutePath, '-latest', '-products', '*', '-requires', component,
        '-property', 'installationPath'], null, null, 'vswhere').readLines().find { it.trim() }?.trim()
    if (!installation) throw new GradleException("Building Jasper's launcher needs the MSVC component ${component}. ${hint}")
    File vcvarsall = new File(installation, 'VC\\Auxiliary\\Build\\vcvarsall.bat')
    File script = launcherBuild.get().file('msvc-environment.bat').asFile
    script.parentFile.mkdirs()
    script.setText("@echo off\r\ncall \"${vcvarsall.absolutePath}\" ${arm ? 'arm64' : 'x64'} >nul\r\nif errorlevel 1 exit /b 1\r\nset\r\n", 'UTF-8')
    Map<String, String> environment = new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER)
    runLauncherTool(['cmd.exe', '/d', '/c', script.absolutePath], null, null, 'vcvarsall', hint).readLines().each { String line ->
        int equals = line.indexOf('=')
        if (equals > 0) environment.put(line.substring(0, equals), line.substring(equals + 1))
    }
    msvcCache.environment = environment
    environment
}

def msvcTool = { String name ->
    String path = msvc().get('PATH') ?: ''
    String found = path.split(';').collect { it.trim() }.findAll { it }.collect { new File(it, name) }.find { it.isFile() }?.absolutePath
    if (found == null) throw new GradleException("${name} is not on the MSVC environment's PATH. ${launcherToolchainHint('windows')}")
    found
}

// The JDK's own defines for jpackage's launcher (make/autoconf/flags-cflags.m4).
def launcherDefines = { String os ->
    List<String> common = ['_LITTLE_ENDIAN', '_LP64=1', 'NDEBUG']
    switch (os) {
        case 'macos': return ['_ALLBSD_SOURCE', '_DARWIN_UNLIMITED_SELECT', 'MACOSX'] + common
        case 'linux': return ['_GNU_SOURCE', '_REENTRANT', '_FILE_OFFSET_BITS=64', 'LINUX'] + common
        default: return ['WIN32_LEAN_AND_MEAN', '_WIN32_WINNT=0x0602', '_CRT_DECLARE_NONSTDC_NAMES', '_CRT_NONSTDC_NO_WARNINGS',
            '_CRT_SECURE_NO_WARNINGS', 'WIN32', 'IAL', 'WINDOWS', 'UNICODE', '_UNICODE'] + common
    }
}

def launcherMacArch = { host -> host.architecture == 'aarch64' ? 'arm64' : 'x86_64' }

// Compiles sources into one binary for this host with the flags of the JDK's make/modules/jdk.jpackage/Lib.gmk.
// spec: kind ('executable' | 'shared'), output, work (object directory), sources (.c/.cpp), includes, defines,
// strict (sources compiled with warnings as errors), exportAll (test binaries: default visibility, no stripping),
// libraries (linker arguments), rpaths (macOS), subsystem ('CONSOLE' | 'WINDOWS') and resources (.res) on Windows.
def nativeBuild = { Map spec ->
    def host = launcherHost()
    File output = spec.output
    File work = spec.work
    work.mkdirs()
    output.parentFile.mkdirs()
    List<File> sources = spec.sources
    Set<String> strict = (spec.strict ?: []).collect { it.absolutePath } as Set
    File include = new File(launcherSdk.get(), 'include')
    List<File> includes = (spec.includes ?: []) +
        [include, new File(include, host.os == 'macos' ? 'darwin' : (host.os == 'windows' ? 'win32' : 'linux'))]
    List<String> defines = launcherDefines(host.os) + (spec.defines ?: [])
    boolean exportAll = spec.exportAll ?: false
    String hint = launcherToolchainHint(host.os)
    List<String> objects = []
    sources.eachWithIndex { File source, int index ->
        boolean cxx = source.name.endsWith('.cpp')
        boolean warningsAreErrors = strict.contains(source.absolutePath)
        File object = new File(work, "${index}-${source.name}.${host.os == 'windows' ? 'obj' : 'o'}")
        objects << object.absolutePath
        List<String> command
        if (host.os == 'windows') {
            command = [msvcTool('cl.exe'), '/nologo', '/c', '/O1', '/MT', '/utf-8', '/validate-charset', '/Zc:preprocessor'] +
                (cxx ? ['/std:c++14', '/EHsc', '/Zc:inline', '/Zc:throwingNew', '/permissive-', '/Zc:wchar_t-'] : ['/std:c11']) +
                (warningsAreErrors ? ['/W4', '/WX'] : ['/W3']) +
                defines.collect { "/D${it}".toString() } + includes.collect { "/I${it.absolutePath}".toString() } +
                ["/Fo${object.absolutePath}".toString(), source.absolutePath]
        } else {
            List<String> compiler = host.os == 'macos' ? ['/usr/bin/xcrun', cxx ? 'clang++' : 'clang'] : [cxx ? 'g++' : 'gcc']
            List<String> flags = host.os == 'macos'
                ? ['-arch', launcherMacArch(host), '-mmacosx-version-min=11.0', '-O2', '-fstack-protector', '-Wall']
                : ['-O2', '-fstack-protector', '-fPIC', '-Wall']
            command = compiler + flags + (exportAll ? [] : ['-fvisibility=hidden']) + [cxx ? '-std=c++14' : '-std=c11'] +
                (warningsAreErrors ? ['-Wextra', '-Werror'] : []) +
                defines.collect { "-D${it}".toString() } + includes.collect { "-I${it.absolutePath}".toString() } +
                ['-c', source.absolutePath, '-o', object.absolutePath]
        }
        runLauncherTool(command, host.os == 'windows' ? msvc() : null, null, "compiling ${source.name}", hint)
    }
    boolean anyCxx = sources.any { it.name.endsWith('.cpp') }
    List<String> link
    switch (host.os) {
        case 'macos':
            link = ['/usr/bin/xcrun', 'clang++', '-arch', launcherMacArch(host), '-mmacosx-version-min=11.0', '-Wl,-reproducible'] +
                (exportAll ? [] : ['-Wl,-dead_strip']) + objects + (spec.libraries ?: []) +
                (spec.rpaths ?: []).collect { "-Wl,-rpath,${it}".toString() } + ['-o', output.absolutePath]
            break
        case 'linux':
            link = (spec.kind == 'shared'
                    ? ['g++', '-shared', '-Wl,--exclude-libs,ALL', '-static-libstdc++', '-static-libgcc']
                    : [anyCxx ? 'g++' : 'gcc', '-pie']) +
                ['-Wl,-z,defs', '-Wl,-z,relro', '-Wl,-z,now', '-Wl,--no-as-needed'] + objects + (spec.libraries ?: []) +
                ['-o', output.absolutePath]
            break
        default:
            link = [msvcTool('cl.exe'), '/nologo'] + objects + (spec.resources ?: []).collect { it.absolutePath } +
                ["/Fe${output.absolutePath}".toString(), '/link', '/OPT:REF', '/INCREMENTAL:NO', '/STACK:1048576'] +
                (spec.subsystem ? ["/SUBSYSTEM:${spec.subsystem}".toString()] : []) + (spec.libraries ?: [])
    }
    runLauncherTool(link, host.os == 'windows' ? msvc() : null, work, "linking ${output.name}", hint)
    if (host.os == 'macos' && !exportAll) {
        runLauncherTool(['/usr/bin/xcrun', 'strip', '-S', output.absolutePath], null, null, "stripping ${output.name}", hint)
    }
    output
}

// ---- Native tests of the options logic ----

def testLauncherCore = tasks.register('testLauncherCore') {
    group = 'verification'
    description = "Compiles and runs the native tests of the launcher's options logic."
    inputs.dir(jasperNative).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir(launcherTestSource).withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.file(launcherBuild.map { it.file('core-test/passed') })
    doLast {
        File work = launcherBuild.get().dir('core-test').asFile
        project.delete(work)
        List<File> sources = [new File(launcherTestSource, 'vmoptions_core_test.cpp'), new File(jasperNative, 'JasperVmOptionsCore.cpp')]
        File test = nativeBuild(kind: 'executable', exportAll: true, sources: sources, strict: sources, includes: [jasperNative],
            output: new File(work, launcherHost().os == 'windows' ? 'vmoptions-core-test.exe' : 'vmoptions-core-test'),
            work: new File(work, 'objects'))
        String output = runLauncherTool([test.absolutePath, new File(launcherTestSource, 'digest-vectors.txt').absolutePath],
            null, null, "the launcher's options tests")
        logger.lifecycle(output.trim())
        // The JDK's defines are macros in every source (-DLINUX, /DWINDOWS). Check the core and its test once more with
        // the other hosts' defines, so a name that collides with one fails here, not only on that host's CI.
        def host = launcherHost()
        if (host.os != 'windows') {
            List<String> compiler = host.os == 'macos' ? ['/usr/bin/xcrun', 'clang++'] : ['g++']
            ['macos', 'linux', 'windows'].findAll { it != host.os }.each { String other ->
                runLauncherTool(compiler + ['-std=c++14', '-fsyntax-only', '-Wall', '-Wextra', '-Werror'] +
                    launcherDefines(other).collect { "-D${it}".toString() } + ["-I${jasperNative.absolutePath}".toString()] +
                    sources.collect { it.absolutePath }, null, null, "checking the options logic with ${other}'s defines",
                    launcherToolchainHint(host.os))
            }
        }
        new File(work, 'passed').text = output
    }
}
tasks.named('check') { dependsOn(testLauncherCore) }
```

- [ ] **Step 2: Run the test to see it fail**

Run: `./gradlew :jasper-app:testLauncherCore`
Expected: FAIL — `JasperVmOptionsCore.cpp` does not exist (the compile of source index 1 cannot open the file).

- [ ] **Step 3: Implement the core**

Create `packaging/launcher/jasper/JasperVmOptionsCore.cpp`:

```cpp
// See JasperVmOptionsCore.h.
#include "JasperVmOptionsCore.h"

#include <cstdint>
#include <cstdio>

namespace jasper {
namespace vmoptions {

namespace {

const char* const WHITESPACE = " \t\r\n\f\v";

std::string trim(const std::string& value) {
    const std::string::size_type first = value.find_first_not_of(WHITESPACE);
    if (first == std::string::npos) {
        return std::string();
    }
    const std::string::size_type last = value.find_last_not_of(WHITESPACE);
    return value.substr(first, last - first + 1);
}

bool startsWith(const std::string& value, const std::string& prefix) {
    return value.size() >= prefix.size() && value.compare(0, prefix.size(), prefix) == 0;
}

bool endsWith(const std::string& value, const std::string& suffix) {
    return value.size() >= suffix.size()
        && value.compare(value.size() - suffix.size(), suffix.size(), suffix) == 0;
}

const std::string* variable(const Environment& env, const char* name) {
    const Environment::const_iterator found = env.find(name);
    return found == env.end() ? nullptr : &found->second;
}

std::string value(const Environment& env, const char* name) {
    const std::string* found = variable(env, name);
    return found == nullptr ? std::string() : *found;
}

bool isAbsolute(Os os, const std::string& path) {
    if (os != Os::Windows) {
        return startsWith(path, "/");
    }
    if (startsWith(path, "\\\\") || startsWith(path, "//")) {
        return true;
    }
    const bool drive = path.size() >= 3
        && ((path[0] >= 'A' && path[0] <= 'Z') || (path[0] >= 'a' && path[0] <= 'z')) && path[1] == ':';
    return drive && (path[2] == '\\' || path[2] == '/');
}

std::string join(Os os, const std::string& directory, const std::string& child) {
    if (directory.empty()) {
        return child;
    }
    const char last = directory[directory.size() - 1];
    if (last == '/' || (os == Os::Windows && last == '\\')) {
        return directory + child;
    }
    return directory + (os == Os::Windows ? "\\" : "/") + child;
}

std::string inJasperDirectory(Os os, const std::string& configurationRoot) {
    return join(os, join(os, configurationRoot, "jasper"), FILE_NAME);
}

bool isValidUtf8(const std::string& bytes) {
    std::string::size_type i = 0;
    while (i < bytes.size()) {
        const unsigned char lead = static_cast<unsigned char>(bytes[i]);
        if (lead < 0x80u) {
            ++i;
            continue;
        }
        std::string::size_type extra = 0;
        std::uint32_t codePoint = 0;
        if (lead >= 0xC2u && lead <= 0xDFu) {
            extra = 1;
            codePoint = lead & 0x1Fu;
        } else if (lead >= 0xE0u && lead <= 0xEFu) {
            extra = 2;
            codePoint = lead & 0x0Fu;
        } else if (lead >= 0xF0u && lead <= 0xF4u) {
            extra = 3;
            codePoint = lead & 0x07u;
        } else {
            return false;
        }
        if (bytes.size() - i <= extra) {
            return false;
        }
        for (std::string::size_type k = 1; k <= extra; ++k) {
            const unsigned char next = static_cast<unsigned char>(bytes[i + k]);
            if ((next & 0xC0u) != 0x80u) {
                return false;
            }
            codePoint = (codePoint << 6) | (next & 0x3Fu);
        }
        const bool overlong = (extra == 2 && codePoint < 0x800u) || (extra == 3 && codePoint < 0x10000u);
        const bool surrogate = codePoint >= 0xD800u && codePoint <= 0xDFFFu;
        if (overlong || surrogate || codePoint > 0x10FFFFu) {
            return false;
        }
        i += extra + 1;
    }
    return true;
}

} // namespace

std::string userFilePath(Os os, const Environment& env, const std::string& workingDirectory,
                         const std::function<bool(const std::string&)>& isRegularFile) {
    const std::string chosen = value(env, VM_OPTIONS_VARIABLE);
    if (!trim(chosen).empty() && isRegularFile(chosen)) {
        return chosen;
    }
    const std::string home = trim(value(env, HOME_VARIABLE));
    if (!home.empty()) {
        return join(os, isAbsolute(os, home) ? home : join(os, workingDirectory, home), FILE_NAME);
    }
    if (os == Os::Windows) {
        const std::string appData = value(env, "APPDATA");
        if (isAbsolute(os, appData)) {
            return inJasperDirectory(os, appData);
        }
        const std::string profile = value(env, "USERPROFILE");
        return profile.empty() ? std::string()
                               : inJasperDirectory(os, join(os, join(os, profile, "AppData"), "Roaming"));
    }
    if (os == Os::Linux) {
        const std::string xdg = value(env, "XDG_CONFIG_HOME");
        if (isAbsolute(os, xdg)) {
            return inJasperDirectory(os, xdg);
        }
    }
    const std::string userHome = value(env, "HOME");
    return userHome.empty() ? std::string() : inJasperDirectory(os, join(os, userHome, ".config"));
}

UserFile parse(const std::string& bytes) {
    UserFile result;
    std::string text = bytes;
    if (startsWith(text, "\xEF\xBB\xBF")) {
        text.erase(0, 3);
    }
    if (text.find('\0') != std::string::npos || !isValidUtf8(text)) {
        result.corrupted = true;
        return result;
    }
    std::string::size_type start = 0;
    while (start <= text.size()) {
        std::string::size_type end = text.find('\n', start);
        if (end == std::string::npos) {
            end = text.size();
        }
        const std::string line = trim(text.substr(start, end - start));
        if (!line.empty() && line[0] != '#') {
            result.lines.push_back(line);
        }
        start = end + 1;
    }
    return result;
}

std::vector<std::string> merge(const std::vector<std::string>& bundled, const std::vector<std::string>& user) {
    bool dropCollector = false;
    bool dropXms = false;
    bool dropXmx = false;
    for (const std::string& line : user) {
        if (startsWith(line, "-XX:+") && endsWith(line, "GC")) {
            dropCollector = true;
        }
        if (startsWith(line, "-XX:InitialRAMPercentage=")) {
            dropXms = true;
        }
        if (startsWith(line, "-XX:MinRAMPercentage=") || startsWith(line, "-XX:MaxRAMPercentage=")) {
            dropXmx = true;
        }
    }
    std::vector<std::string> result;
    for (const std::string& option : bundled) {
        const bool collector = startsWith(option, "-XX:+") && endsWith(option, "GC");
        if ((dropCollector && collector) || (dropXms && startsWith(option, "-Xms"))
                || (dropXmx && startsWith(option, "-Xmx"))) {
            continue;
        }
        result.push_back(option);
    }
    result.insert(result.end(), user.begin(), user.end());
    return result;
}

std::string digest(const std::vector<std::string>& lines) {
    std::uint64_t hash = 0xcbf29ce484222325ULL;
    const auto add = [&hash](unsigned char byte) {
        hash ^= byte;
        hash *= 0x100000001b3ULL;
    };
    for (const std::string& line : lines) {
        for (const char c : line) {
            add(static_cast<unsigned char>(c));
        }
        add('\n');
    }
    char text[17];
    std::snprintf(text, sizeof(text), "%016llx", static_cast<unsigned long long>(hash));
    return std::string(text);
}

Plan plan(const std::vector<std::string>& bundled, const std::string& path, const UserFile* file,
          const Environment& env) {
    Plan result;
    result.javaOptions = bundled;
    const std::string* skip = variable(env, SKIP_VARIABLE);
    result.removeSkipVariable = skip != nullptr;
    const std::string reason = skip == nullptr ? std::string() : trim(*skip);
    const bool applied = reason.empty() && !path.empty() && file != nullptr && !file->corrupted && !file->lines.empty();
    if (applied) {
        result.javaOptions = merge(bundled, file->lines);
    }
    if (!path.empty()) {
        result.javaOptions.push_back("-Djasper.vmOptionsFile=" + path);
    }
    if (!reason.empty()) {
        result.javaOptions.push_back("-Djasper.vmOptionsSkipped=" + reason);
    } else if (!path.empty() && file != nullptr && file->corrupted) {
        result.javaOptions.push_back("-Djasper.vmOptionsFile.corrupted=true");
    } else if (applied) {
        result.javaOptions.push_back("-Djasper.vmOptionsDigest=" + digest(file->lines));
    }
    return result;
}

} // namespace vmoptions
} // namespace jasper
```

- [ ] **Step 4: Run the test to see it pass**

Run: `./gradlew :jasper-app:testLauncherCore`
Expected: BUILD SUCCESSFUL, logging `<N> checks, 0 failures` (N is about 50).

- [ ] **Step 5: Commit**

```bash
git add packaging/launcher/jasper packaging/launcher/test gradle/launcher.gradle
git commit -F - <<'EOF'
Launcher: the jasper.vmoptions logic and the host toolchain

JasperVmOptionsCore decides the options file's location, parses it and
merges it over the bundled options IntelliJ's way, with its digest and the
skip variable; testLauncherCore (in check) compiles its native tests with
clang, gcc or MSVC and runs them against the shared digest vectors.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 3: The exit guard

**Files:**
- Create: `packaging/launcher/jasper/jasper_launch_guard.h`, `packaging/launcher/jasper/jasper_launch_guard.c`
- Create: `packaging/launcher/test/guard_harness.c`, `packaging/launcher/test/GuardProbe.java`
- Modify: `gradle/launcher.gradle` (import at the top; block at the end)

**Interfaces:**
- Consumes: Task 2's `nativeBuild`, `launcherHost`, `launcherSdk`, `jasperNative`, `launcherTestSource`, `launcherBuild`,
  `launcherToolchains`, `launcherJava`.
- Produces (C): `void jasperGuardBeforeLaunch(int argc, char** argv)`, `void jasperGuardAfterLaunch(void)`,
  `void jasperGuardSetRespawn(void (*replacement)(void))`. Gradle: task `compileLauncherProbes` (JavaCompile, output
  `build/launcher/probes`), task `testLauncherGuard` (in `check`).

- [ ] **Step 1: Write the failing test**

Create `packaging/launcher/test/GuardProbe.java`:

```java
/** The guard test's application: "exit" calls System.exit(3), "throw" throws, anything else returns normally. */
public final class GuardProbe {
    private GuardProbe() {
    }

    public static void main(String[] arguments) {
        String mode = arguments.length == 0 ? "" : arguments[0];
        if (mode.equals("exit")) {
            System.exit(3);
        }
        if (mode.equals("throw")) {
            throw new IllegalStateException("guard probe");
        }
    }
}
```

Create `packaging/launcher/test/guard_harness.c`:

```c
/*
 * Test harness for jasper_launch_guard.c (packaging/launcher/README.md). Arms the guard as jvmLauncherStartJvm does,
 * with the re-spawn replaced by writing the file named by GUARD_HARNESS_MARKER, then runs JLI_Launch from the libjli
 * named by GUARD_HARNESS_JLI with this program's arguments as java's. On macOS libjli calls main again on a new thread
 * with the same arguments, so the settings come from the environment.
 */
#include "jasper_launch_guard.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "jni.h"

#ifdef _WIN32
#include <windows.h>
#else
#include <dlfcn.h>
#endif

typedef int (JNICALL *LaunchFunction)(int, char**, int, const char**, int, const char**, const char*, const char*,
                                      const char*, const char*, jboolean, jboolean, jboolean, jint);

static void writeMarker(void) {
    const char* marker = getenv("GUARD_HARNESS_MARKER");
    FILE* file = marker == NULL ? NULL : fopen(marker, "w");
    if (file != NULL) {
        fputs("respawned\n", file);
        fclose(file);
    }
}

static LaunchFunction loadLaunch(const char* library) {
#ifdef _WIN32
    char directory[MAX_PATH];
    char* separator;
    HMODULE jli;
    strncpy(directory, library, sizeof(directory) - 1);
    directory[sizeof(directory) - 1] = '\0';
    separator = strrchr(directory, '\\');
    if (separator == NULL) {
        separator = strrchr(directory, '/');
    }
    if (separator != NULL) {
        *separator = '\0';
        SetDllDirectoryA(directory);
    }
    jli = LoadLibraryA(library);
    return jli == NULL ? NULL : (LaunchFunction) GetProcAddress(jli, "JLI_Launch");
#else
    void* jli = dlopen(library, RTLD_NOW | RTLD_GLOBAL);
    return jli == NULL ? NULL : (LaunchFunction) dlsym(jli, "JLI_Launch");
#endif
}

int main(int argc, char** argv) {
    const char* library = getenv("GUARD_HARNESS_JLI");
    LaunchFunction launch = library == NULL ? NULL : loadLaunch(library);
    int status;
    if (launch == NULL) {
        fprintf(stderr, "guard-harness: cannot load JLI_Launch from GUARD_HARNESS_JLI\n");
        return 65;
    }
    jasperGuardSetRespawn(writeMarker);
    jasperGuardBeforeLaunch(argc, argv);
    status = launch(argc, argv, 0, NULL, 0, NULL, "", "", "java", "java", JNI_FALSE, JNI_FALSE, JNI_FALSE, 0);
    jasperGuardAfterLaunch();
    return status;
}
```

Create `packaging/launcher/jasper/jasper_launch_guard.h`:

```c
/*
 * Jasper's launch guard (packaging/launcher/README.md, spec section 4): when the JVM cannot be created with the
 * user's options, start this launcher again with JASPER_VM_OPTIONS_SKIP=launch-failed.
 */
#ifndef jasper_launch_guard_h
#define jasper_launch_guard_h

#ifdef __cplusplus
extern "C" {
#endif

/* Arms the guard when argv carries -Djasper.vmOptionsDigest= (the user's options were applied). Idempotent. */
void jasperGuardBeforeLaunch(int argc, char** argv);

/* Records that JLI_Launch returned: the JVM was created and has finished. */
void jasperGuardAfterLaunch(void);

/* Tests only: replaces the re-spawn of this executable. */
void jasperGuardSetRespawn(void (*replacement)(void));

#ifdef __cplusplus
}
#endif

#endif /* jasper_launch_guard_h */
```

Append to the **top** of `gradle/launcher.gradle`, after the existing imports:

```groovy
import java.util.concurrent.TimeUnit
```

Append to the **end** of `gradle/launcher.gradle`:

```groovy
// ---- The exit guard, against the toolchain JDK's real libjli ----

def compileLauncherProbes = tasks.register('compileLauncherProbes', JavaCompile) {
    description = "Compiles the Java probes the launcher's tests run."
    source = fileTree(launcherTestSource) { include '*.java' }
    classpath = files()
    destinationDirectory.set(launcherBuild.map { it.dir('probes') })
    javaCompiler.set(launcherToolchains.compilerFor(launcherJava.toolchain))
    options.release.set(25)
    options.encoding = 'UTF-8'
}

def testLauncherGuard = tasks.register('testLauncherGuard') {
    group = 'verification'
    description = "Checks the launcher's exit guard with the toolchain JDK's libjli: re-spawn only when no JVM was created."
    dependsOn(compileLauncherProbes)
    inputs.dir(jasperNative).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir(launcherTestSource).withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.file(launcherBuild.map { it.file('guard-test/passed') })
    doLast {
        def host = launcherHost()
        File work = launcherBuild.get().dir('guard-test').asFile
        project.delete(work)
        List<File> sources = [new File(launcherTestSource, 'guard_harness.c'), new File(jasperNative, 'jasper_launch_guard.c')]
        File harness = nativeBuild(kind: 'executable', exportAll: true, sources: sources, strict: sources, includes: [jasperNative],
            libraries: host.os == 'linux' ? ['-ldl'] : [],
            output: new File(work, host.os == 'windows' ? 'guard-harness.exe' : 'guard-harness'), work: new File(work, 'objects'))
        File sdk = launcherSdk.get()
        File jli = host.os == 'windows' ? new File(sdk, 'bin/jli.dll') : new File(sdk, host.os == 'macos' ? 'lib/libjli.dylib' : 'lib/libjli.so')
        String probes = compileLauncherProbes.get().destinationDirectory.get().asFile.absolutePath
        String armed = '-Djasper.vmOptionsDigest=0123456789abcdef'
        def scenarios = [
            [name: 'malformed heap value', arguments: ['-Xmx3q', armed, '-cp', probes, 'GuardProbe'], respawn: true, exit: null],
            [name: 'conflicting collectors', arguments: ['-XX:+UseSerialGC', '-XX:+UseParallelGC', armed, '-cp', probes, 'GuardProbe'],
                respawn: true, exit: null],
            [name: 'System.exit', arguments: [armed, '-cp', probes, 'GuardProbe', 'exit'], respawn: false, exit: 3],
            [name: 'normal return', arguments: [armed, '-cp', probes, 'GuardProbe'], respawn: false, exit: 0],
            [name: 'main throws', arguments: [armed, '-cp', probes, 'GuardProbe', 'throw'], respawn: false, exit: 1],
            [name: 'unarmed malformed heap value', arguments: ['-Xmx3q', '-cp', probes, 'GuardProbe'], respawn: false, exit: null],
            [name: 'no libjvm loaded', arguments: ['-XXaltjvm=/nonexistent/jvm', armed, '-cp', probes, 'GuardProbe'], respawn: false, exit: null],
        ]
        List<String> failures = []
        scenarios.eachWithIndex { Map scenario, int index ->
            File marker = new File(work, "respawn-${index}")
            File log = new File(work, "output-${index}.txt")
            def builder = new ProcessBuilder([harness.absolutePath] + scenario.arguments).redirectErrorStream(true).redirectOutput(log)
            ['JAVA_TOOL_OPTIONS', '_JAVA_OPTIONS', 'JDK_JAVA_OPTIONS', 'CLASSPATH'].each { builder.environment().remove(it) }
            builder.environment().put('GUARD_HARNESS_JLI', jli.absolutePath)
            builder.environment().put('GUARD_HARNESS_MARKER', marker.absolutePath)
            Process process = builder.start()
            boolean exited = process.waitFor(60, TimeUnit.SECONDS)
            if (!exited) process.destroyForcibly()
            String output = log.getText('UTF-8')
            if (!exited) {
                failures << "${scenario.name}: the harness did not exit within 60 s\n${output}".toString()
                return
            }
            int exit = process.exitValue()
            if (marker.isFile() != scenario.respawn) {
                failures << "${scenario.name}: re-spawn ${marker.isFile() ? 'happened' : 'did not happen'} (exit ${exit})\n${output}".toString()
            }
            if (scenario.exit != null && exit != scenario.exit) failures << "${scenario.name}: exit ${exit}, expected ${scenario.exit}\n${output}".toString()
            if (scenario.exit == null && exit == 0) failures << "${scenario.name}: the JVM started\n${output}".toString()
        }
        if (failures) throw new GradleException('testLauncherGuard:\n' + failures.join('\n'))
        new File(work, 'passed').text = "${scenarios.size()} scenarios\n"
    }
}
tasks.named('check') { dependsOn(testLauncherGuard) }
```

- [ ] **Step 2: Run the test to see it fail**

Run: `./gradlew :jasper-app:testLauncherGuard`
Expected: FAIL — `jasper_launch_guard.c` does not exist.

- [ ] **Step 3: Implement the guard**

Create `packaging/launcher/jasper/jasper_launch_guard.c`:

```c
/*
 * See jasper_launch_guard.h. libjli calls exit() when the JVM cannot be created, so the launcher never regains
 * control; an exit handler tells that case apart: JLI_Launch has not returned and no JVM was created. System.exit
 * leaves its JVM counted, and a normal return passes through jasperGuardAfterLaunch first.
 */
#include "jasper_launch_guard.h"

#include <stdlib.h>
#include <string.h>

#include "jni.h"

#ifdef _WIN32
#include <windows.h>
#else
#include <dlfcn.h>
#include <spawn.h>
#include <stdint.h>
#include <stdio.h>
#include <sys/types.h>
#include <unistd.h>
#endif

#ifdef __APPLE__
#include <crt_externs.h>
#include <mach-o/dyld.h>
#endif

#define JASPER_DIGEST_PREFIX "-Djasper.vmOptionsDigest="
#define JASPER_SKIP_VARIABLE "JASPER_VM_OPTIONS_SKIP"
#define JASPER_SKIP_REASON "launch-failed"

typedef jint (JNICALL *CreatedJavaVmsFunction)(JavaVM**, jsize, jsize*);

static volatile int armed;
static volatile int registered;
static volatile int returned;
static void (*respawnReplacement)(void);

/* The loaded libjvm's own JNI_GetCreatedJavaVMs, or NULL when no libjvm is loaded. Asked of that image only: macOS's
   libjli exports a wrapper of the same name that would load the default libjvm on demand. */
static CreatedJavaVmsFunction loadedJvmFunction(void) {
#if defined(_WIN32)
    HMODULE jvm = GetModuleHandleW(L"jvm.dll");
    return jvm == NULL ? NULL : (CreatedJavaVmsFunction) GetProcAddress(jvm, "JNI_GetCreatedJavaVMs");
#elif defined(__APPLE__)
    static const char suffix[] = "/libjvm.dylib";
    const size_t suffixLength = sizeof(suffix) - 1;
    uint32_t images = _dyld_image_count();
    uint32_t i;
    for (i = 0; i < images; ++i) {
        const char* name = _dyld_get_image_name(i);
        size_t length = name == NULL ? 0 : strlen(name);
        if (length >= suffixLength && strcmp(name + length - suffixLength, suffix) == 0) {
            void* jvm = dlopen(name, RTLD_LAZY | RTLD_NOLOAD);
            return jvm == NULL ? NULL : (CreatedJavaVmsFunction) dlsym(jvm, "JNI_GetCreatedJavaVMs");
        }
    }
    return NULL;
#else
    void* jvm = dlopen("libjvm.so", RTLD_LAZY | RTLD_NOLOAD);
    return jvm == NULL ? NULL : (CreatedJavaVmsFunction) dlsym(jvm, "JNI_GetCreatedJavaVMs");
#endif
}

/* The number of created Java VMs, or -1 when it cannot be told (no libjvm is loaded). */
static int createdJavaVms(void) {
    CreatedJavaVmsFunction function = loadedJvmFunction();
    JavaVM* vms[1];
    jsize count = 0;
    if (function == NULL || function(vms, 1, &count) != JNI_OK) {
        return -1;
    }
    return (int) count;
}

#ifdef _WIN32

static void respawnSelf(void) {
    static wchar_t path[32768];
    DWORD capacity = (DWORD) (sizeof(path) / sizeof(path[0]));
    DWORD length = GetModuleFileNameW(NULL, path, capacity);
    wchar_t* commandLine;
    STARTUPINFOW startup;
    PROCESS_INFORMATION process;
    if (length == 0 || length >= capacity) {
        return;
    }
    if (!SetEnvironmentVariableA(JASPER_SKIP_VARIABLE, JASPER_SKIP_REASON)) {
        return;
    }
    commandLine = _wcsdup(GetCommandLineW());
    if (commandLine == NULL) {
        return;
    }
    ZeroMemory(&startup, sizeof(startup));
    startup.cb = sizeof(startup);
    if (CreateProcessW(path, commandLine, NULL, NULL, FALSE, 0, NULL, NULL, &startup, &process)) {
        CloseHandle(process.hThread);
        CloseHandle(process.hProcess);
    }
    free(commandLine);
}

#else

#ifdef __APPLE__

static char** processArguments(void) {
    return *_NSGetArgv();
}

static char** processEnvironment(void) {
    return *_NSGetEnviron();
}

static int executablePath(char* path, size_t size) {
    uint32_t length = (uint32_t) size;
    return _NSGetExecutablePath(path, &length) == 0 ? 0 : -1;
}

#else

extern char** environ;

/* The original command line, read from /proc; arguments beyond 64 KiB or 1023 entries are dropped. */
static char** processArguments(void) {
    static char buffer[65536];
    static char* arguments[1024];
    size_t used;
    size_t i = 0;
    int count = 0;
    FILE* file = fopen("/proc/self/cmdline", "rb");
    if (file == NULL) {
        return NULL;
    }
    used = fread(buffer, 1, sizeof(buffer) - 1, file);
    fclose(file);
    buffer[used] = '\0';
    while (i < used && count < 1023) {
        arguments[count++] = buffer + i;
        i += strlen(buffer + i) + 1;
    }
    arguments[count] = NULL;
    return count == 0 ? NULL : arguments;
}

static char** processEnvironment(void) {
    return environ;
}

static int executablePath(char* path, size_t size) {
    ssize_t length = readlink("/proc/self/exe", path, size - 1);
    if (length <= 0) {
        return -1;
    }
    path[length] = '\0';
    return 0;
}

#endif

static void respawnSelf(void) {
    static char path[4096];
    char** arguments;
    pid_t child;
    if (executablePath(path, sizeof(path)) != 0) {
        return;
    }
    arguments = processArguments();
    if (arguments == NULL || setenv(JASPER_SKIP_VARIABLE, JASPER_SKIP_REASON, 1) != 0) {
        return;
    }
    if (posix_spawn(&child, path, NULL, NULL, arguments, processEnvironment()) != 0) {
        return;
    }
}

#endif

static void onExit(void) {
    if (!armed || returned || createdJavaVms() != 0) {
        return;
    }
    armed = 0;
    if (respawnReplacement != NULL) {
        respawnReplacement();
    } else {
        respawnSelf();
    }
}

static void registerExitHandler(void) {
#ifdef _WIN32
    /* jli.dll and jvm.dll call the shared UCRT's exit(), which runs the UCRT's process-wide table but not this
       statically linked CRT's own atexit table, so register there too. */
    typedef int (__cdecl *CrtAtExitFunction)(void (__cdecl *)(void));
    HMODULE ucrt = GetModuleHandleW(L"ucrtbase.dll");
    CrtAtExitFunction crtAtExit = ucrt == NULL ? NULL : (CrtAtExitFunction) GetProcAddress(ucrt, "_crt_atexit");
    if (crtAtExit != NULL) {
        crtAtExit(onExit);
    }
#endif
    atexit(onExit);
}

void jasperGuardBeforeLaunch(int argc, char** argv) {
    int i;
    for (i = 0; i < argc; ++i) {
        if (argv[i] != NULL && strncmp(argv[i], JASPER_DIGEST_PREFIX, sizeof(JASPER_DIGEST_PREFIX) - 1) == 0) {
            armed = 1;
            break;
        }
    }
    if (armed && !registered) {
        registered = 1;
        registerExitHandler();
    }
}

void jasperGuardAfterLaunch(void) {
    returned = 1;
}

void jasperGuardSetRespawn(void (*replacement)(void)) {
    respawnReplacement = replacement;
}
```

- [ ] **Step 4: Run the test to see it pass**

Run: `./gradlew :jasper-app:testLauncherGuard`
Expected: BUILD SUCCESSFUL (7 scenarios). The two failing JVMs print `Invalid maximum heap size: -Xmx3q` and
`Conflicting collector combinations…` into the captured output; that is expected.

- [ ] **Step 5: Commit**

```bash
git add packaging/launcher/jasper packaging/launcher/test gradle/launcher.gradle
git commit -F - <<'EOF'
Launcher: the exit guard that recovers from rejected VM options

When the launcher applied the user's options and the JVM was never
created, an exit handler starts the launcher again with
JASPER_VM_OPTIONS_SKIP=launch-failed. testLauncherGuard (in check) drives
it through the toolchain JDK's real libjli: a malformed value and a
collector conflict re-spawn; System.exit, a normal return, a throwing main
and an unarmed launch do not.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 4: Build the launcher with the hooks

**Files:**
- Create: `packaging/launcher/jasper/JasperVmOptions.h`, `packaging/launcher/jasper/JasperVmOptions.cpp`
- Modify: `packaging/launcher/upstream/src/jdk.jpackage/share/native/applauncher/AppLauncher.cpp` (2 hook lines)
- Modify: `packaging/launcher/upstream/src/jdk.jpackage/share/native/applauncher/JvmLauncherLib.c` (3 hook lines)
- Modify: `gradle/launcher.gradle` (`launcherHookedFiles`; block at the end), `packaging/launcher/README.md`

**Interfaces:**
- Consumes: Task 2's core API and `nativeBuild`, `msvcTool`, `msvc`; Task 3's guard API.
- Produces (C++): `void jasper::applyUserVmOptions(CfgFile& cfgFile)`. Gradle: task `buildLauncher`; local and project
  extra `launcherResources` (Provider<Directory>, `build/launcher/resources`); project extra closure
  `jpackageLauncherOption()` → `"-J--patch-module=jdk.jpackage=<launcherResources>"`. Binaries land in
  `build/launcher/resources/jdk/jpackage/internal/resources/`: `jpackageapplauncher` (macOS, Linux),
  `libjpackageapplauncheraux.so` (Linux), `jpackageapplauncher.exe` and `jpackageapplauncherw.exe` (Windows).

- [ ] **Step 1: Declare the hooked files and see the verification fail**

In `gradle/launcher.gradle`, replace:

```groovy
def launcherHookedFiles = [] as Set<String>
```

with:

```groovy
def launcherHookedFiles = ['src/jdk.jpackage/share/native/applauncher/AppLauncher.cpp',
    'src/jdk.jpackage/share/native/applauncher/JvmLauncherLib.c'] as Set<String>
```

Run: `./gradlew :jasper-app:verifyLauncherSources`
Expected: FAIL with `files with JASPER-HOOK lines: []; expected [src/jdk.jpackage/share/native/applauncher/AppLauncher.cpp, src/jdk.jpackage/share/native/applauncher/JvmLauncherLib.c]`.

- [ ] **Step 2: Add the hook lines**

In `packaging/launcher/upstream/src/jdk.jpackage/share/native/applauncher/AppLauncher.cpp`, after the line
`#include "FileUtils.h"` add:

```cpp
#include "JasperVmOptions.h" // JASPER-HOOK
```

and replace:

```cpp
    } else {
        (*jvm).initFromConfigFile(cfgFile);
    }
```

with:

```cpp
    } else {
        jasper::applyUserVmOptions(cfgFile); // JASPER-HOOK: merge jasper.vmoptions (packaging/launcher/README.md)
        (*jvm).initFromConfigFile(cfgFile);
    }
```

In `packaging/launcher/upstream/src/jdk.jpackage/share/native/applauncher/JvmLauncherLib.c`, after the line
`#include "JvmLauncher.h"` add:

```c
#include "jasper_launch_guard.h" /* JASPER-HOOK */
```

replace:

```c
    exitCode = (*((JLI_LaunchFuncType)JLI_Launch))(
```

with:

```c
    jasperGuardBeforeLaunch(jvmArgs->jliLaunchArgc, jvmArgs->jliLaunchArgv); /* JASPER-HOOK */
    exitCode = (*((JLI_LaunchFuncType)JLI_Launch))(
```

and replace (the end of that call):

```c
        JNI_FALSE,
        0);

    return exitCode;
```

with:

```c
        JNI_FALSE,
        0);
    jasperGuardAfterLaunch(); /* JASPER-HOOK */

    return exitCode;
```

Run: `./gradlew :jasper-app:verifyLauncherSources`
Expected: BUILD SUCCESSFUL (the hooked files hash to upstream once the hook lines are removed).

- [ ] **Step 3: Write the wrapper**

Create `packaging/launcher/jasper/JasperVmOptions.h`:

```cpp
// Applies the user's jasper.vmoptions to the launcher's cfg (packaging/launcher/README.md). Called from
// AppLauncher::createJvmLauncher; never throws.
#ifndef JasperVmOptions_h
#define JasperVmOptions_h

class CfgFile;

namespace jasper {

/** Replaces the cfg's java-options with the bundled options merged with the user's file (JasperVmOptionsCore). */
void applyUserVmOptions(CfgFile& cfgFile);

} // namespace jasper

#endif // JasperVmOptions_h
```

Create `packaging/launcher/jasper/JasperVmOptions.cpp`:

```cpp
// See JasperVmOptions.h. Environment and file access around JasperVmOptionsCore, with jpackage's own helpers.
#include "JasperVmOptions.h"

#include <cstdio>
#include <new>
#include <string>
#include <vector>

#include "CfgFile.h"
#include "FileUtils.h"
#include "JasperVmOptionsCore.h"
#include "Log.h"
#include "SysInfo.h"
#include "tstrings.h"

#ifdef _WIN32
#include <windows.h>
#else
#include <stdlib.h>
#endif

namespace jasper {

namespace {

vmoptions::Os hostOs() {
#if defined(_WIN32)
    return vmoptions::Os::Windows;
#elif defined(__APPLE__)
    return vmoptions::Os::Mac;
#else
    return vmoptions::Os::Linux;
#endif
}

std::string utf8(const tstring& value) {
    return tstrings::toUtf8(value);
}

tstring native(const std::string& value) {
    return tstrings::fromUtf8(value);
}

vmoptions::Environment environment() {
    vmoptions::Environment env;
    const char* const names[] = { vmoptions::VM_OPTIONS_VARIABLE, vmoptions::SKIP_VARIABLE, vmoptions::HOME_VARIABLE,
        "HOME", "XDG_CONFIG_HOME", "APPDATA", "USERPROFILE" };
    for (const char* name : names) {
        const tstring key = native(name);
        if (SysInfo::isEnvVariableSet(key)) {
            env[name] = utf8(SysInfo::getEnvVariable(std::nothrow, key));
        }
    }
    return env;
}

bool isRegularFile(const std::string& path) {
    const tstring value = native(path);
    return FileUtils::isFileExists(value) && !FileUtils::isDirectory(value);
}

// The file's bytes; false when it is not a regular file or cannot be read.
bool readBytes(const std::string& path, std::string& bytes) {
    if (!isRegularFile(path)) {
        return false;
    }
#ifdef _WIN32
    FILE* file = _wfopen(native(path).c_str(), L"rb");
#else
    FILE* file = std::fopen(path.c_str(), "rb");
#endif
    if (file == nullptr) {
        return false;
    }
    char buffer[4096];
    size_t count;
    while ((count = std::fread(buffer, 1, sizeof(buffer), file)) > 0) {
        bytes.append(buffer, count);
    }
    const bool failed = std::ferror(file) != 0;
    std::fclose(file);
    return !failed;
}

void removeVariable(const char* name) {
#ifdef _WIN32
    SetEnvironmentVariableW(native(name).c_str(), nullptr);
#else
    unsetenv(name);
#endif
}

} // namespace

void applyUserVmOptions(CfgFile& cfgFile) {
    try {
        std::vector<std::string> bundled;
        const CfgFile::Properties& section = cfgFile.getProperties(SectionName::JavaOptions);
        const CfgFile::Properties::const_iterator options = section.find(PropertyName::javaOptions);
        if (options != section.end()) {
            for (const tstring& option : options->second) {
                bundled.push_back(utf8(option));
            }
        }
        const vmoptions::Environment env = environment();
        const std::string path = vmoptions::userFilePath(hostOs(), env, utf8(FileUtils::toAbsolutePath(tstring())),
            isRegularFile);
        std::string bytes;
        vmoptions::UserFile file;
        const bool exists = !path.empty() && readBytes(path, bytes);
        if (exists) {
            file = vmoptions::parse(bytes);
        }
        const vmoptions::Plan plan = vmoptions::plan(bundled, path, exists ? &file : nullptr, env);
        if (plan.removeSkipVariable) {
            removeVariable(vmoptions::SKIP_VARIABLE);
        }
        tstring_array merged;
        for (const std::string& option : plan.javaOptions) {
            merged.push_back(native(option));
        }
        cfgFile.setPropertyValue(SectionName::JavaOptions, PropertyName::javaOptions, merged);
        LOG_TRACE(tstrings::any() << "Jasper VM options file: " << native(path));
    } catch (...) {
        LOG_ERROR(tstrings::any() << "Jasper: the user's VM options were ignored after an error");
    }
}

} // namespace jasper
```

- [ ] **Step 4: Write the build task**

Append to the **end** of `gradle/launcher.gradle`:

```groovy
// ---- Jasper's launcher for this host ----

def launcherResources = launcherBuild.map { it.dir('resources') }
ext.launcherResources = launcherResources
// jpackage's own option that makes it copy Jasper's launcher instead of its built-in one.
ext.jpackageLauncherOption = { -> "-J--patch-module=jdk.jpackage=${launcherResources.get().asFile.absolutePath}".toString() }
def jpackageNative = new File(upstreamRoot, 'src/jdk.jpackage')
def nativeDirectory = { String os, String kind -> new File(jpackageNative, "${os}/native/${kind}") }
// Every C and C++ source directly in the directories, as the JDK's make compiles them.
def sourcesIn = { List<File> directories ->
    directories.collectMany { File directory -> (directory.listFiles() ?: []).findAll { it.name.endsWith('.c') || it.name.endsWith('.cpp') } }
        .sort { it.absolutePath }
}
String launcherUpstreamVersion = '25.0.4.1'

def windowsVersionResource = { File work ->
    work.mkdirs()
    File resource = new File(work, 'version.res')
    runLauncherTool([msvcTool('rc.exe'), '/nologo', '/fo', resource.absolutePath, '/DJDK_FTYPE=0x1L',
        "/DJDK_FVER=${launcherUpstreamVersion.replace('.', ',')}".toString(), "/DJDK_VER=${launcherUpstreamVersion}".toString(),
        "/DJDK_VERSION_STRING=${launcherUpstreamVersion}".toString(), '/DJDK_COMPANY=Jasper', '/DJDK_NAME=Jasper',
        '/DJDK_FILEDESC=Jasper', '/DJDK_COPYRIGHT=Jasper', '/DJDK_INTERNAL_NAME=jpackageapplauncher',
        '/DJDK_FNAME=jpackageapplauncher.exe', new File(upstreamRoot, 'src/java.base/windows/native/common/version.rc').absolutePath],
        msvc(), work, 'rc version.rc', launcherToolchainHint('windows'))
    resource
}

def embedWindowsManifest = { File exe, String name ->
    runLauncherTool([msvcTool('mt.exe'), '-nologo', '-manifest',
        new File(upstreamRoot, 'src/java.base/windows/native/launcher/java.manifest').absolutePath,
        "-identity:${name}.exe, version=${launcherUpstreamVersion}".toString(), "-outputresource:${exe.absolutePath};#1".toString()],
        msvc(), exe.parentFile, "mt ${name}.exe", launcherToolchainHint('windows'))
}

def buildLauncher = tasks.register('buildLauncher') {
    group = 'build'
    description = "Builds Jasper's jpackage launcher for this host into build/launcher/resources."
    dependsOn(verifyLauncherSources)
    inputs.dir(upstreamRoot).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir(jasperNative).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.property('sdk', launcherSdk.map { it.absolutePath })
    inputs.property('host', providers.provider { launcherHost().toString() })
    outputs.dir(launcherResources)
    doLast {
        def host = launcherHost()
        project.delete(launcherResources.get().asFile)
        File output = new File(launcherResources.get().asFile, 'jdk/jpackage/internal/resources')
        output.mkdirs()
        File work = launcherBuild.get().dir('objects').asFile
        project.delete(work)
        File core = new File(jasperNative, 'JasperVmOptionsCore.cpp')
        File guard = new File(jasperNative, 'jasper_launch_guard.c')
        List<File> jasperSources = [new File(jasperNative, 'JasperVmOptions.cpp'), core, guard]
        List<File> strict = [core, guard]
        switch (host.os) {
            case 'macos':
                List<File> directories = [nativeDirectory('macosx', 'applauncher'), nativeDirectory('share', 'applauncher'),
                    nativeDirectory('macosx', 'common'), nativeDirectory('unix', 'common'), nativeDirectory('share', 'common')]
                nativeBuild(kind: 'executable', output: new File(output, 'jpackageapplauncher'), work: new File(work, 'macos'),
                    sources: sourcesIn(directories) + jasperSources, strict: strict, includes: directories + [jasperNative],
                    libraries: ['-framework', 'Cocoa'], rpaths: ['@executable_path/../Frameworks/', '@executable_path/../PlugIns/'])
                break
            case 'linux':
                List<File> launcherDirectories = [nativeDirectory('linux', 'applauncher'), nativeDirectory('share', 'applauncher'),
                    nativeDirectory('linux', 'common'), nativeDirectory('unix', 'common'), nativeDirectory('share', 'common')]
                nativeBuild(kind: 'executable', output: new File(output, 'jpackageapplauncher'), work: new File(work, 'linux-launcher'),
                    sources: sourcesIn(launcherDirectories).findAll { it.name.endsWith('.c') } + [guard], strict: [guard],
                    includes: launcherDirectories + [jasperNative], libraries: ['-ldl', '-lpthread'])
                List<File> libraryDirectories = [nativeDirectory('linux', 'libapplauncher')] + launcherDirectories
                nativeBuild(kind: 'shared', output: new File(output, 'libjpackageapplauncheraux.so'), work: new File(work, 'linux-library'),
                    sources: sourcesIn(libraryDirectories).findAll { !(it.name in ['LinuxLauncher.c', 'LinuxPackage.c']) } + jasperSources,
                    strict: strict, includes: libraryDirectories + [jasperNative], libraries: ['-ldl', '-lpthread'])
                break
            default:
                List<File> directories = [nativeDirectory('windows', 'applauncher'), nativeDirectory('share', 'applauncher'),
                    nativeDirectory('windows', 'common'), nativeDirectory('share', 'common')]
                File resource = windowsVersionResource(new File(work, 'resources'))
                [[name: 'jpackageapplauncher', subsystem: 'CONSOLE', defines: []],
                 [name: 'jpackageapplauncherw', subsystem: 'WINDOWS', defines: ['JP_LAUNCHERW']]].each { Map variant ->
                    File exe = new File(output, "${variant.name}.exe")
                    nativeBuild(kind: 'executable', output: exe, work: new File(work, variant.name),
                        sources: sourcesIn(directories) + jasperSources, strict: strict, includes: directories + [jasperNative],
                        defines: variant.defines, subsystem: variant.subsystem, resources: [resource],
                        libraries: ['msi.lib', 'ole32.lib', 'shell32.lib', 'shlwapi.lib', 'user32.lib', 'advapi32.lib', 'kernel32.lib'])
                    embedWindowsManifest(exe, variant.name)
                }
        }
        List<File> binaries = (output.listFiles() ?: []).findAll { it.isFile() }
        binaries.each { File binary ->
            if (!new String(Files.readAllBytes(binary.toPath()), StandardCharsets.ISO_8859_1).contains('JASPER_VM_OPTIONS_SKIP')) {
                throw new GradleException("buildLauncher: ${binary.name} lacks Jasper's hook")
            }
        }
        logger.lifecycle("Built Jasper's launcher: ${binaries*.name.sort().join(', ')}")
    }
}
```

- [ ] **Step 5: Build it**

Run: `./gradlew :jasper-app:buildLauncher`
Expected on macOS: BUILD SUCCESSFUL, `Built Jasper's launcher: jpackageapplauncher`. Then:

Run: `file jasper-app/build/launcher/resources/jdk/jpackage/internal/resources/jpackageapplauncher && xcrun vtool -show-build jasper-app/build/launcher/resources/jdk/jpackage/internal/resources/jpackageapplauncher`
Expected: a Mach-O 64-bit executable for the host architecture; `minos 11.0` and `sdk` equal to `xcrun --show-sdk-version`.

(On Linux the task builds `jpackageapplauncher` and `libjpackageapplauncheraux.so`; on Windows the two `.exe` files.
CI's `check` exercises them through Task 5.)

- [ ] **Step 6: Document the hooks and the build**

In `packaging/launcher/README.md`, replace the final line
`Then re-apply the \`JASPER-HOOK\` lines, update the tag and commit in this file, and run \`./gradlew :jasper-app:check\`.`
with the following (its first line replaces that line):

~~~markdown
Then re-apply the hook lines below, update the tag and commit in this file, and run `./gradlew :jasper-app:check`.

## Hooks

Two upstream files carry Jasper's lines, each marked `JASPER-HOOK` (`launcherHookedFiles` in `gradle/launcher.gradle`):

- `share/native/applauncher/AppLauncher.cpp`: `#include "JasperVmOptions.h"`, and in `createJvmLauncher`, just before
  `(*jvm).initFromConfigFile(cfgFile)`, the call `jasper::applyUserVmOptions(cfgFile)`.
- `share/native/applauncher/JvmLauncherLib.c`: `#include "jasper_launch_guard.h"`, and around the `JLI_Launch` call in
  `jvmLauncherStartJvm`, `jasperGuardBeforeLaunch(jvmArgs->jliLaunchArgc, jvmArgs->jliLaunchArgv)` before and
  `jasperGuardAfterLaunch()` after.

On macOS libjli calls the launcher's `main` again on a new thread; that second call also goes through
`jvmLauncherStartJvm`, so the guard sees `JLI_Launch` return on every platform.

## Jasper's code

- `jasper/JasperVmOptionsCore.{h,cpp}`: the options file's location, format, IntelliJ's merge, the digest and the skip
  variable, as pure functions over UTF-8 strings.
- `jasper/JasperVmOptions.{h,cpp}`: reads the environment and the file with jpackage's helpers and rewrites the cfg's
  `java-options`; it never throws, and on any error the bundled options stand.
- `jasper/jasper_launch_guard.{h,c}`: armed when the JVM arguments carry `-Djasper.vmOptionsDigest=`. Its exit handler
  re-spawns the launcher with `JASPER_VM_OPTIONS_SKIP=launch-failed` when `JLI_Launch` never returned and the loaded
  libjvm's own `JNI_GetCreatedJavaVMs` reports no JVM; with no libjvm loaded it does nothing. On Windows it also
  registers with `ucrtbase.dll`'s `_crt_atexit`, because `jli.dll` calls the shared UCRT's `exit()`. HotSpot's own
  initialization failures end with `_exit` and never reach it.

## Build

`buildLauncher` (`gradle/launcher.gradle`) compiles the launcher for the host with the flags of the JDK's
`make/modules/jdk.jpackage/Lib.gmk`: C++14 and C11, `-O2` (MSVC `/O1`), the JDK's defines, hidden visibility, and on
Windows the static CRT. Upstream files compile at the JDK's warning level; the core and the guard with warnings as
errors. Outputs go to `build/launcher/resources/jdk/jpackage/internal/resources/`, where
`jpackage -J--patch-module=jdk.jpackage=build/launcher/resources` takes them instead of its own:

| Host | Compiler | Outputs |
|---|---|---|
| macOS | Xcode command line tools (`xcrun clang`), minimum macOS 11.0, linked against the installed SDK | `jpackageapplauncher` |
| Linux | `gcc` and `g++`; the library links libstdc++ and libgcc statically | `jpackageapplauncher`, `libjpackageapplauncheraux.so` |
| Windows | MSVC from `vswhere` and `vcvarsall.bat`, with `version.rc` and `java.manifest` embedded | `jpackageapplauncher.exe`, `jpackageapplauncherw.exe` |
~~~

- [ ] **Step 7: Commit**

```bash
git add packaging/launcher gradle/launcher.gradle
git commit -F - <<'EOF'
Launcher: build jpackage's launcher with Jasper's hooks

Two marked lines in AppLauncher.cpp apply jasper.vmoptions to the cfg and
three in JvmLauncherLib.c arm the exit guard around JLI_Launch.
buildLauncher compiles the launcher for the host with the JDK's flags into
build/launcher/resources, for jpackage's -J--patch-module.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 5: End-to-end test with a probe image

**Files:**
- Create: `packaging/launcher/test/LauncherProbe.java`
- Modify: `gradle/launcher.gradle` (block at the end), `packaging/launcher/README.md`

**Interfaces:**
- Consumes: Task 3's `compileLauncherProbes`; Task 4's `buildLauncher`, `launcherResources`, `jpackageLauncherOption`;
  Task 2's `runLauncherTool`, `launcherHost`, `launcherSdk`.
- Produces: tasks `launcherProbeJar` and `verifyLauncherImage` (in `check`).

- [ ] **Step 1: Write the failing test**

Create `packaging/launcher/test/LauncherProbe.java`:

```java
import java.io.IOException;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** The launcher image test's application: appends what the launcher handed the JVM to $PROBE_OUTPUT, then "end". */
public final class LauncherProbe {
    private LauncherProbe() {
    }

    public static void main(String[] arguments) throws IOException {
        StringBuilder report = new StringBuilder();
        line(report, "vmOptionsFile", System.getProperty("jasper.vmOptionsFile"));
        line(report, "digest", System.getProperty("jasper.vmOptionsDigest"));
        line(report, "corrupted", System.getProperty("jasper.vmOptionsFile.corrupted"));
        line(report, "skipped", System.getProperty("jasper.vmOptionsSkipped"));
        line(report, "skipVariable", System.getenv("JASPER_VM_OPTIONS_SKIP"));
        line(report, "marker", System.getProperty("probe.marker"));
        for (String input : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
            line(report, "input", input);
        }
        for (GarbageCollectorMXBean collector : ManagementFactory.getGarbageCollectorMXBeans()) {
            line(report, "gc", collector.getName());
        }
        for (String argument : arguments) {
            line(report, "argument", argument);
        }
        report.append("end\n");
        Files.writeString(Path.of(System.getenv("PROBE_OUTPUT")), report, StandardCharsets.UTF_8,
            StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    private static void line(StringBuilder report, String key, String value) {
        report.append(key).append('=').append(value).append('\n');
    }
}
```

Append to the **end** of `gradle/launcher.gradle`:

```groovy
// ---- End to end: a probe app packaged with Jasper's launcher ----

def launcherProbeJar = tasks.register('launcherProbeJar', Jar) {
    description = "Packs the launcher image test's probe application."
    from(compileLauncherProbes)
    include('LauncherProbe*.class')
    archiveFileName.set('launcher-probe.jar')
    destinationDirectory.set(launcherBuild.map { it.dir('probe-input') })
}

def verifyLauncherImage = tasks.register('verifyLauncherImage') {
    group = 'verification'
    description = "Packages a probe app with Jasper's launcher and checks jasper.vmoptions end to end, headlessly."
    dependsOn(buildLauncher, launcherProbeJar)
    inputs.dir(launcherResources).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(launcherProbeJar.flatMap { it.archiveFile })
    outputs.file(launcherBuild.map { it.file('image-test/passed') })
    doLast {
        def host = launcherHost()
        File work = launcherBuild.get().dir('image-test').asFile
        project.delete(work)
        work.mkdirs()
        File images = new File(work, 'images')
        File probeInput = launcherProbeJar.get().archiveFile.get().asFile.parentFile
        String jpackage = new File(launcherSdk.get(), host.os == 'windows' ? 'bin/jpackage.exe' : 'bin/jpackage').absolutePath
        // Bundled options the user's lines can replace: -Xmx256m and -XX:+UseSerialGC, plus IntelliJ's ignore flag.
        def image = { String name, boolean console ->
            List<String> command = [jpackage, jpackageLauncherOption(), '--type', 'app-image', '--name', name,
                '--dest', images.absolutePath, '--input', probeInput.absolutePath, '--main-jar', 'launcher-probe.jar',
                '--main-class', 'LauncherProbe', '--add-modules', 'java.base,java.management',
                '--java-options', '-Xmx256m', '--java-options', '-XX:+UseSerialGC', '--java-options', '-XX:+IgnoreUnrecognizedVMOptions']
            if (host.os == 'macos') command += ['--mac-package-identifier', "dev.jasper.${name.toLowerCase(Locale.ROOT)}".toString()]
            if (host.os == 'windows' && console) command += ['--win-console']
            runLauncherTool(command, null, null, "jpackage ${name}")
            host.os == 'macos' ? new File(images, "${name}.app/Contents/MacOS/${name}")
                : (host.os == 'windows' ? new File(images, "${name}/${name}.exe") : new File(images, "${name}/bin/${name}"))
        }
        File console = image('LauncherProbe', true)

        // The build's environment without anything that steers the launcher or the JVM, plus the scenario's variables.
        def environment = { Map<String, String> extra ->
            Map<String, String> result = new LinkedHashMap<String, String>(System.getenv())
            ['JASPER_HOME', 'JASPER_VM_OPTIONS', 'JASPER_VM_OPTIONS_SKIP', 'JAVA_TOOL_OPTIONS', '_JAVA_OPTIONS',
                'JDK_JAVA_OPTIONS', 'XDG_CONFIG_HOME'].each { result.remove(it) }
            result.putAll(extra)
            result
        }
        // Runs the launcher; returns the single report the probe appended (key -> values). Waits for a re-spawned start.
        def launch = { String scenario, File launcher, Map<String, String> env, List<String> arguments = [] ->
            File report = new File(work, "${scenario}.report")
            File log = new File(work, "${scenario}.log")
            def builder = new ProcessBuilder([launcher.absolutePath] + arguments).redirectErrorStream(true).redirectOutput(log)
            builder.environment().clear()
            builder.environment().putAll(env)
            builder.environment().put('PROBE_OUTPUT', report.absolutePath)
            Process process = builder.start()
            if (!process.waitFor(60, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                throw new GradleException("verifyLauncherImage: ${scenario}: the launcher did not exit\n${log.text}")
            }
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60)
            while (!(report.isFile() && report.getText('UTF-8').contains('end\n'))) {
                if (System.nanoTime() > deadline) {
                    throw new GradleException("verifyLauncherImage: ${scenario}: no report (exit ${process.exitValue()})\n${log.text}")
                }
                Thread.sleep(100)
            }
            Thread.sleep(500) // a second report would mean a second start
            String text = report.getText('UTF-8')
            int reports = text.readLines().count { it == 'end' }
            if (reports != 1) throw new GradleException("verifyLauncherImage: ${scenario}: ${reports} reports\n${text}")
            Map<String, List<String>> values = [:]
            text.readLines().findAll { it != 'end' }.each { String line ->
                int equals = line.indexOf('=')
                values.computeIfAbsent(line.substring(0, equals)) { [] } << line.substring(equals + 1)
            }
            values
        }
        def one = { Map<String, List<String>> report, String key -> report[key] ? report[key][0] : null }
        List<String> failures = []
        def expect = { String scenario, boolean condition, String message, Map report ->
            if (!condition) failures << "${scenario}: ${message}\n  report: ${report}".toString()
        }
        def home = { String name, String content ->
            File directory = new File(work, name)
            directory.mkdirs()
            if (content != null) new File(directory, 'jasper.vmoptions').setText(content, 'UTF-8')
            directory
        }
        def file = { File directory -> new File(directory, 'jasper.vmoptions').absolutePath }

        File none = home('no-file', null)
        def report = launch('no-file', console, environment([JASPER_HOME: none.absolutePath]))
        expect('no file', one(report, 'vmOptionsFile') == file(none), 'reports the Jasper home file', report)
        expect('no file', one(report, 'digest') == 'null' && report.input.contains('-Xmx256m'), 'only the bundled options', report)

        File ordered = home('ordered', '# heap\r\n\r\n  -Xmx300m  \r\n-Dprobe.marker=a b\r\n')
        report = launch('ordered', console, environment([JASPER_HOME: ordered.absolutePath]))
        List<String> sequence = ['-Xmx256m', '-Xmx300m', '-Dprobe.marker=a b', "-Djasper.vmOptionsFile=${file(ordered)}".toString(),
            '-Djasper.vmOptionsDigest=f88af5c65b6cd728']
        List<Integer> positions = sequence.collect { report.input.indexOf(it) }
        expect('ordered', one(report, 'digest') == 'f88af5c65b6cd728', 'the digest of the applied lines', report)
        expect('ordered', one(report, 'marker') == 'a b', 'a -D value keeps its space', report)
        expect('ordered', !positions.contains(-1) && positions == positions.toSorted(),
            "bundled options, then the user's lines, then the bookkeeping properties: ${sequence}", report)

        File collector = home('collector', '-XX:+UseParallelGC\n')
        report = launch('collector', console, environment([JASPER_HOME: collector.absolutePath]))
        expect('collector', one(report, 'skipped') == 'null' && !report.input.contains('-XX:+UseSerialGC') &&
            report.gc.any { it.startsWith('PS ') }, "the user's collector replaces the bundled one", report)

        File percentage = home('percentage', '-XX:MaxRAMPercentage=10\n')
        report = launch('percentage', console, environment([JASPER_HOME: percentage.absolutePath]))
        expect('percentage', !report.input.contains('-Xmx256m') && report.input.contains('-XX:MaxRAMPercentage=10'),
            'MaxRAMPercentage replaces the bundled -Xmx', report)

        File corrupted = home('corrupted', '-Xmx300m\n\u0000\n')
        report = launch('corrupted', console, environment([JASPER_HOME: corrupted.absolutePath]))
        expect('corrupted', one(report, 'corrupted') == 'true' && one(report, 'digest') == 'null' &&
            !report.input.contains('-Xmx300m'), 'a NUL character makes the launcher ignore the file', report)

        File chosen = home('chosen-home', '-Dprobe.marker=home\n')
        File other = new File(work, 'other.vmoptions')
        other.setText('-Dprobe.marker=other\n', 'UTF-8')
        report = launch('chosen', console, environment([JASPER_HOME: chosen.absolutePath, JASPER_VM_OPTIONS: other.absolutePath]))
        expect('JASPER_VM_OPTIONS', one(report, 'vmOptionsFile') == other.absolutePath && one(report, 'marker') == 'other',
            'JASPER_VM_OPTIONS names the file', report)
        report = launch('chosen-missing', console, environment([JASPER_HOME: chosen.absolutePath,
            JASPER_VM_OPTIONS: new File(work, 'missing.vmoptions').absolutePath]))
        expect('missing JASPER_VM_OPTIONS', one(report, 'vmOptionsFile') == file(chosen) && one(report, 'marker') == 'home',
            'a JASPER_VM_OPTIONS naming no file falls back to the Jasper home', report)

        File user = new File(work, 'user-home')
        File defaultFile = host.os == 'windows' ? new File(user, 'jasper\\jasper.vmoptions') : new File(user, '.config/jasper/jasper.vmoptions')
        defaultFile.parentFile.mkdirs()
        defaultFile.setText('-Dprobe.marker=default\n', 'UTF-8')
        report = launch('default', console, environment(host.os == 'windows' ? [APPDATA: user.absolutePath] : [HOME: user.absolutePath]))
        expect('default location', one(report, 'vmOptionsFile') == defaultFile.absolutePath && one(report, 'marker') == 'default',
            "the OS default in the Jasper home", report)

        File skipped = home('skipped', '-Xmx300m\n')
        report = launch('skipped', console, environment([JASPER_HOME: skipped.absolutePath, JASPER_VM_OPTIONS_SKIP: 'manual']))
        expect('JASPER_VM_OPTIONS_SKIP', one(report, 'skipped') == 'manual' && one(report, 'skipVariable') == 'null' &&
            !report.input.contains('-Xmx300m'), 'skips the file and keeps the variable from the JVM', report)

        File rejected = home('rejected', '-Xmx3q\n')
        report = launch('rejected', console, environment([JASPER_HOME: rejected.absolutePath]), ['alpha', 'with space'])
        expect('rejected options', one(report, 'skipped') == 'launch-failed' && one(report, 'skipVariable') == 'null' &&
            !report.input.contains('-Xmx3q'), 'one more start without the options', report)
        expect('rejected options', report.argument == ['alpha', 'with space'], 'the arguments survive the second start', report)

        File directory = home('directory', null)
        new File(directory, 'jasper.vmoptions').mkdirs()
        report = launch('directory', console, environment([JASPER_HOME: directory.absolutePath]))
        expect('directory', one(report, 'digest') == 'null' && one(report, 'vmOptionsFile') == file(directory),
            'a directory named jasper.vmoptions counts as no file', report)

        // A home with a space and a non-ASCII letter. POSIX passes the name in the build's file-name encoding.
        if (host.os == 'windows' || System.getProperty('sun.jnu.encoding', '').equalsIgnoreCase('UTF-8')) {
            File unicode = home('J\u00fcrgen home', '-Dprobe.marker=unicode\n')
            report = launch('unicode', console, environment([JASPER_HOME: unicode.absolutePath]))
            expect('non-ASCII home', one(report, 'marker') == 'unicode', 'the file is read', report)
            // Windows hands the JVM its options in the ANSI code page, so only POSIX compares the reported path.
            if (host.os != 'windows') expect('non-ASCII home', one(report, 'vmOptionsFile') == file(unicode), 'the path is reported', report)
        } else {
            logger.lifecycle("verifyLauncherImage: skipped the non-ASCII home (file names are ${System.getProperty('sun.jnu.encoding')})")
        }

        if (host.os == 'windows') {
            File windowed = image('LauncherProbeGui', false)
            report = launch('windowed-rejected', windowed, environment([JASPER_HOME: rejected.absolutePath]), ['alpha'])
            expect('windowed launcher', one(report, 'skipped') == 'launch-failed' && report.argument == ['alpha'],
                'the windowed launcher starts once more without the options', report)
        }

        if (failures) throw new GradleException('verifyLauncherImage:\n' + failures.join('\n'))
        new File(work, 'passed').text = 'passed\n'
    }
}
tasks.named('check') { dependsOn(verifyLauncherImage) }
```

- [ ] **Step 2: Run it and confirm it fails without the hook**

Temporarily remove the hook call line `jasper::applyUserVmOptions(cfgFile); // JASPER-HOOK: …` from `AppLauncher.cpp`
(keep the `#include`), then:

Run: `./gradlew :jasper-app:verifyLauncherImage`
Expected: FAIL. `verifyLauncherSources` passes (only the other hook lines remain, and the files still carry
`JASPER-HOOK`), and the image test reports, among others,
`no file: reports the Jasper home file` (no `jasper.vmOptionsFile` property) and `ordered: the digest of the applied lines`.

Restore the line exactly as Task 4 Step 2 wrote it.

- [ ] **Step 3: Run it to see it pass**

Run: `./gradlew :jasper-app:verifyLauncherImage`
Expected: BUILD SUCCESSFUL. On macOS this builds `LauncherProbe.app` with Jasper's launcher (no window opens; the
probe is headless) and runs 12 scenarios.

- [ ] **Step 4: Document the tests**

Append to `packaging/launcher/README.md`:

~~~markdown
## Tests

All in `check`, on every OS:

- `verifyLauncherSources`: `upstream/` against `SHA256SUMS` and the hooked-file list.
- `testLauncherCore`: native tests of the core (`test/vmoptions_core_test.cpp`), including the digest vectors in
  `test/digest-vectors.txt`, which the application's tests read too.
- `testLauncherGuard`: `test/guard_harness.c` arms the guard and runs the toolchain JDK's real `JLI_Launch` with
  `test/GuardProbe.java`. A malformed `-Xmx` and conflicting collectors re-spawn; `System.exit`, a normal return, a
  throwing `main`, an unarmed launch and a launch that fails before any libjvm loads do not.
- `verifyLauncherImage`: packages `test/LauncherProbe.java` with Jasper's launcher (`jpackage
  -J--patch-module=jdk.jpackage=…`) and checks the location rules, the merge order and filters, a corrupted file, the
  skip variable, the fallback start with the arguments intact, a directory in the file's place and a non-ASCII home.
  On Windows it also checks the windowed launcher.
~~~

- [ ] **Step 5: Commit**

```bash
git add packaging/launcher gradle/launcher.gradle
git commit -F - <<'EOF'
Launcher: end-to-end test with a probe image

verifyLauncherImage (in check) packages a headless probe app with Jasper's
launcher and checks jasper.vmoptions end to end: locations, merge order and
filters, corruption, the skip variable, the fallback start with its
arguments, a directory in the file's place and a non-ASCII home.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 6: Package Jasper with its launcher

**Files:**
- Modify: `gradle/packaging.gradle`, `gradle/packaging-support.gradle`, `gradle/packaging-support-test.gradle`
- Modify: `gradle/mac-launcher-identity.gradle` (comment), `jasper-app/build.gradle.kts`
- Modify: `jasper-app/src/main/java/dev/jasper/app/benchmark/StartupRuns.java`,
  `jasper-app/src/test/java/dev/jasper/app/benchmark/StartupBenchTest.java`
- Modify: `.github/workflows/linux-packages.yml`
- Modify: `docs/packaging.md`, `docs/app-maintenance.md`, `AGENTS.md`, `docs/STATUS.md`,
  `docs/superpowers/specs/2026-10-13-jasper-custom-vm-options-design.md`, this plan's status line

**Interfaces:**
- Consumes: Task 4's `buildLauncher`, `launcherResources`, `jpackageLauncherOption()`; Tasks 2–3's test tasks.
- Produces: `aotTrainingEnvironment(Map inherited, String javaToolOptions, File vmOptions)`;
  `StartupRuns.openCommand(Path app, String javaToolOptions, Path home, boolean background)`,
  `StartupRuns.isolatedVmOptions(Path home)`.

- [ ] **Step 1: Write the failing tests**

In `gradle/packaging-support-test.gradle`, replace the training-environment block:

```groovy
        // Training must not see the user's real Jasper home or any JVM option or class path a real launch would not have.
        Map<String, String> inherited = [JASPER_HOME: '/Users/x/real-jasper', _JAVA_OPTIONS: '-Xmx1m', JDK_JAVA_OPTIONS: '-Xms1m',
            CLASSPATH: '/some/classes', JAVA_TOOL_OPTIONS: '-Dinherited=1', HOME: '/Users/x', PATH: '/usr/bin:/bin']
        Map<String, String> training = aotTrainingEnvironment(inherited, '-Dtraining=2')
        ['JASPER_HOME', '_JAVA_OPTIONS', 'JDK_JAVA_OPTIONS', 'CLASSPATH'].each { assert !training.containsKey(it) : "${it} leaked into the training environment" }
        assert training.JAVA_TOOL_OPTIONS == '-Dtraining=2'
        assert training.HOME == '/Users/x' && training.PATH == '/usr/bin:/bin'
        assert training.keySet() == ['JAVA_TOOL_OPTIONS', 'HOME', 'PATH'] as Set
        assert inherited.size() == 7 && inherited.JASPER_HOME == '/Users/x/real-jasper' : 'the inherited map must not be modified'
```

with:

```groovy
        // Training must not see the user's real Jasper home, their jasper.vmoptions, or any JVM option or class path a
        // real launch would not have.
        Map<String, String> inherited = [JASPER_HOME: '/Users/x/real-jasper', _JAVA_OPTIONS: '-Xmx1m', JDK_JAVA_OPTIONS: '-Xms1m',
            CLASSPATH: '/some/classes', JAVA_TOOL_OPTIONS: '-Dinherited=1', HOME: '/Users/x', PATH: '/usr/bin:/bin',
            JASPER_VM_OPTIONS: '/Users/x/mine.vmoptions', JASPER_VM_OPTIONS_SKIP: 'manual']
        File emptyVmOptions = new File('/work/empty.vmoptions')
        Map<String, String> training = aotTrainingEnvironment(inherited, '-Dtraining=2', emptyVmOptions)
        ['JASPER_HOME', '_JAVA_OPTIONS', 'JDK_JAVA_OPTIONS', 'CLASSPATH', 'JASPER_VM_OPTIONS_SKIP'].each {
            assert !training.containsKey(it) : "${it} leaked into the training environment"
        }
        assert training.JAVA_TOOL_OPTIONS == '-Dtraining=2'
        assert training.JASPER_VM_OPTIONS == emptyVmOptions.absolutePath : 'training must read an empty options file'
        assert training.HOME == '/Users/x' && training.PATH == '/usr/bin:/bin'
        assert training.keySet() == ['JAVA_TOOL_OPTIONS', 'HOME', 'PATH', 'JASPER_VM_OPTIONS'] as Set
        assert inherited.size() == 9 && inherited.JASPER_HOME == '/Users/x/real-jasper' : 'the inherited map must not be modified'
```

In `jasper-app/src/test/java/dev/jasper/app/benchmark/StartupBenchTest.java`, replace the method
`launchesGoThroughLaunchServicesWithTheTraceAndAnIsolatedHome` with:

```java
    @Test void launchesGoThroughLaunchServicesWithTheTraceAndAnIsolatedHome() {
        Path home = Path.of("/tmp/jsb1/home");
        String options = StartupRuns.launchOptions(Path.of("/tmp/jsb1/trace-3.json"), home);
        assertThat(options).isEqualTo("-Djasper.startupTrace=/tmp/jsb1/trace-3.json -Duser.home=/tmp/jsb1/home");
        assertThat(StartupRuns.launchOptions(Path.of("/tmp/t.json"), null)).isEqualTo("-Djasper.startupTrace=/tmp/t.json");
        assertThat(StartupRuns.isolatedVmOptions(home)).isEqualTo(Path.of("/tmp/jsb1/home/.config/jasper/jasper.vmoptions"));
        assertThat(StartupRuns.openCommand(Path.of("/Apps/My Jasper.app"), options, home, false)).containsExactly(
            "open", "-n", "-a", "/Apps/My Jasper.app", "--env", "JAVA_TOOL_OPTIONS=" + options,
            "--env", "JASPER_VM_OPTIONS=/tmp/jsb1/home/.config/jasper/jasper.vmoptions");
        assertThat(StartupRuns.openCommand(Path.of("/A.app"), options, null, true)).containsExactly(
            "open", "-n", "-a", "/A.app", "--env", "JAVA_TOOL_OPTIONS=" + options, "--args", "--background");
        assertThat(StartupRuns.reopenCommand(Path.of("/A.app"))).containsExactly("open", "-a", "/A.app");
    }
```

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :jasper-app:verifyPackagingSupport :jasper-app:test --tests 'dev.jasper.app.benchmark.StartupBenchTest'`
Expected: FAIL — `aotTrainingEnvironment` takes two arguments; `StartupBenchTest` does not compile
(`isolatedVmOptions` and the four-argument `openCommand` do not exist).

- [ ] **Step 3: Isolate training and the benchmark**

In `gradle/packaging-support.gradle`, replace:

```groovy
// The training launch's environment: inherited with JASPER_HOME removed (it would point training at the user's real
// configuration and data), JDK_JAVA_OPTIONS, _JAVA_OPTIONS and CLASSPATH removed (they would change the JVM options
// and class path a real launch has) and JAVA_TOOL_OPTIONS replaced by javaToolOptions. Returns a new map.
ext.aotTrainingEnvironment = { Map<String, String> inherited, String javaToolOptions ->
    Map<String, String> environment = new LinkedHashMap<String, String>(inherited)
    ['JASPER_HOME', 'JDK_JAVA_OPTIONS', '_JAVA_OPTIONS', 'CLASSPATH'].each { environment.remove(it) }
    environment.put('JAVA_TOOL_OPTIONS', javaToolOptions)
    environment
}
```

with:

```groovy
// The training launch's environment: inherited with JASPER_HOME removed (it would point training at the user's real
// configuration and data), JDK_JAVA_OPTIONS, _JAVA_OPTIONS, CLASSPATH and JASPER_VM_OPTIONS_SKIP removed (they would
// change the JVM options and class path a real launch has), JAVA_TOOL_OPTIONS replaced by javaToolOptions and
// JASPER_VM_OPTIONS pointing at vmOptions, an empty file, so the developer's own jasper.vmoptions never shapes the
// cache. Returns a new map.
ext.aotTrainingEnvironment = { Map<String, String> inherited, String javaToolOptions, File vmOptions ->
    Map<String, String> environment = new LinkedHashMap<String, String>(inherited)
    ['JASPER_HOME', 'JDK_JAVA_OPTIONS', '_JAVA_OPTIONS', 'CLASSPATH', 'JASPER_VM_OPTIONS_SKIP'].each { environment.remove(it) }
    environment.put('JAVA_TOOL_OPTIONS', javaToolOptions)
    environment.put('JASPER_VM_OPTIONS', vmOptions.absolutePath)
    environment
}
```

In `runAotTraining` in the same file, replace:

```groovy
    Map<String, String> trainingEnvironment = aotTrainingEnvironment(environment, javaToolOptions(["-XX:AOTCacheOutput=${cache.absolutePath}",
        "-Duser.home=${home.absolutePath}", "-Djasper.startupTrace=${trace.absolutePath}", '-Dapple.awt.UIElement=true']*.toString()))
```

with:

```groovy
    File emptyVmOptions = new File(work, 'empty.vmoptions')
    emptyVmOptions.text = ''
    Map<String, String> trainingEnvironment = aotTrainingEnvironment(environment, javaToolOptions(["-XX:AOTCacheOutput=${cache.absolutePath}",
        "-Duser.home=${home.absolutePath}", "-Djasper.startupTrace=${trace.absolutePath}", '-Dapple.awt.UIElement=true']*.toString()),
        emptyVmOptions)
```

In `jasper-app/src/main/java/dev/jasper/app/benchmark/StartupRuns.java`, replace:

```java
    static List<String> openCommand(Path app, String javaToolOptions, boolean background) {
        List<String> command = new ArrayList<>(List.of("open", "-n", "-a", app.toString(), "--env",
            "JAVA_TOOL_OPTIONS=" + javaToolOptions));
        if (background) command.addAll(List.of("--args", "--background"));
        return command;
```

with:

```java
    /** The empty options file an isolated home gives Jasper's launcher, so the developer's jasper.vmoptions never applies. */
    static Path isolatedVmOptions(Path home) {
        return home.resolve(".config/jasper/jasper.vmoptions");
    }

    static List<String> openCommand(Path app, String javaToolOptions, Path home, boolean background) {
        List<String> command = new ArrayList<>(List.of("open", "-n", "-a", app.toString(), "--env",
            "JAVA_TOOL_OPTIONS=" + javaToolOptions));
        if (home != null) command.addAll(List.of("--env", "JASPER_VM_OPTIONS=" + isolatedVmOptions(home)));
        if (background) command.addAll(List.of("--args", "--background"));
        return command;
```

In the same file, replace the two launches:

```java
        host.run().apply(openCommand(options.app(), launchOptions(traceFile, home), false));
```

with:

```java
        host.run().apply(openCommand(options.app(), launchOptions(traceFile, home), home, false));
```

and:

```java
        host.run().apply(openCommand(options.app(), launchOptions(traceFile, home), true));
```

with:

```java
        host.run().apply(openCommand(options.app(), launchOptions(traceFile, home), home, true));
```

and after the line `Path home = options.realHome() ? null : Files.createDirectories(root.resolve("home"));` add:

```java
            if (home != null) {
                Path vmOptions = isolatedVmOptions(home);
                Files.createDirectories(vmOptions.getParent());
                Files.writeString(vmOptions, "", StandardCharsets.UTF_8);
            }
```

Run: `./gradlew :jasper-app:verifyPackagingSupport :jasper-app:test --tests 'dev.jasper.app.benchmark.StartupBenchTest'`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Bundle the defaults**

In `jasper-app/build.gradle.kts`, replace:

```kotlin
    applicationDefaultJvmArgs = listOf(
        "--enable-native-access=ALL-UNNAMED",
        "-Dapple.awt.application.name=Jasper",
    ) + glassJvmArgs
```

with:

```kotlin
    // The heap default and IntelliJ's tolerance of outdated flags; a user's jasper.vmoptions overrides them.
    applicationDefaultJvmArgs = listOf(
        "--enable-native-access=ALL-UNNAMED",
        "-Dapple.awt.application.name=Jasper",
        "-Xmx2048m",
        "-XX:+IgnoreUnrecognizedVMOptions",
    ) + glassJvmArgs
```

- [ ] **Step 5: Package with Jasper's launcher**

In `gradle/packaging.gradle`:

1. Replace the comment above `installedMacSdkVersion`:

   ```groovy
   // AppKit draws the macOS 26 window controls only for a main executable whose LC_BUILD_VERSION
   // records a macOS 26 or later SDK. jpackage's launcher stub records the SDK JetBrains built the
   // runtime with (13.3 for JBR 25), so every Swing window would get the pre-26 controls. Rewriting
   // that one load command with Apple's vtool and re-signing the bundle is enough; the stub's code is
   // untouched. Needs the Xcode command line tools; without them the image keeps the legacy controls.
   ```

   with:

   ```groovy
   // AppKit draws the macOS 26 window controls only for a main executable whose LC_BUILD_VERSION
   // records a macOS 26 or later SDK. Jasper's launcher (gradle/launcher.gradle) is linked against the
   // installed SDK, which verifyPackage checks. Null without the Xcode command line tools.
   ```

2. Delete the whole `def refreshLauncherSdk = { File image -> … }` closure (from `def refreshLauncherSdk` through its
   closing `}`, just before the `// jpackage puts every jar under --input` comment).

3. Replace the `macLauncherTemplate` closure and its comment:

   ```groovy
   // Read the immutable launcher seed from the selected SDK, independent of the patched image.
   def macLauncherTemplate = { File sdkHome ->
       new ZipFile(new File(sdkHome, 'jmods/jdk.jpackage.jmod')).withCloseable { archive ->
           def entry = archive.getEntry('classes/jdk/jpackage/internal/resources/jpackageapplauncher')
           if (entry == null) throw new GradleException('JBR SDK has no macOS jpackage launcher resource')
           archive.getInputStream(entry).withCloseable { it.readAllBytes() }
       }
   }
   ```

   with:

   ```groovy
   // The identity seed: Jasper's compiled launcher (gradle/launcher.gradle), independent of the patched image.
   def macLauncherTemplate = { ->
       Files.readAllBytes(new File(launcherResources.get().asFile, 'jdk/jpackage/internal/resources/jpackageapplauncher').toPath())
   }
   ```

4. In `packageApp`: after `dependsOn(stage)` add

   ```groovy
       dependsOn(tasks.named('buildLauncher'))
       inputs.dir(launcherResources).withPathSensitivity(PathSensitivity.RELATIVE)
   ```

   delete the line
   `        inputs.file(launcher.map { it.metadata.installationPath.file('jmods/jdk.jpackage.jmod') })`;
   change `List<String> args = [context.jpackage.absolutePath, '--type', 'app-image',` to
   `List<String> args = [context.jpackage.absolutePath, jpackageLauncherOption(), '--type', 'app-image',`;
   delete the line `                refreshLauncherSdk(image)`; and change
   `'dev.jasper.app', macLauncherTemplate(context.sdkHome)))` to `'dev.jasper.app', macLauncherTemplate()))`.

5. In `verifyPackage`: after `dependsOn(packageApp)` add

   ```groovy
       dependsOn(tasks.named('testLauncherCore'), tasks.named('testLauncherGuard'))
   ```

   after the loop that throws `Package verification failed: missing ${candidate}` add:

   ```groovy
           // Jasper's launcher (gradle/launcher.gradle), not jpackage's own: its exit guard names the skip variable.
           if (!new String(Files.readAllBytes(nativeLauncher.toPath()), StandardCharsets.ISO_8859_1).contains('JASPER_VM_OPTIONS_SKIP')) {
               throw new GradleException("Package verification failed: ${nativeLauncher} is not Jasper's launcher")
           }
           if (context.host.os == 'linux') {
               File launcherLibrary = new File(image, 'lib/libapplauncher.so')
               if (!launcherLibrary.isFile() ||
                       !new String(Files.readAllBytes(launcherLibrary.toPath()), StandardCharsets.ISO_8859_1).contains('-Djasper.vmOptionsFile=')) {
                   throw new GradleException("Package verification failed: ${launcherLibrary} is not Jasper's launcher library")
               }
           }
   ```

   replace the comment

   ```groovy
           // Part 2 adds no JVM tuning flags: only the application's own options (on macOS these include the workspace
           // glass options above), the bundled-native options and jpackage's app version may appear (trainAotCache adds
           // -XX:AOTCache on macOS).
   ```

   with:

   ```groovy
           // Only the application's own options (the heap default, -XX:+IgnoreUnrecognizedVMOptions and, on macOS, the
           // workspace glass options above), the bundled-native options and jpackage's app version may appear
           // (trainAotCache adds -XX:AOTCache on macOS). Users add theirs in jasper.vmoptions, not here.
   ```

   and change `macLauncherWithIdentity(executable, identifier, macLauncherTemplate(context.sdkHome))` to
   `macLauncherWithIdentity(executable, identifier, macLauncherTemplate())`.

6. In the Windows installer task change `executeNative([context.jpackage.absolutePath, '--type', 'exe', '--name', 'Jasper',`
   to `executeNative([context.jpackage.absolutePath, jpackageLauncherOption(), '--type', 'exe', '--name', 'Jasper',`, and
   in the Linux package task change `List<String> args = [context.jpackage.absolutePath, '--type', type, '--name', 'Jasper',`
   to `List<String> args = [context.jpackage.absolutePath, jpackageLauncherOption(), '--type', type, '--name', 'Jasper',`
   (packaging from an app image must never put jpackage's own launcher back).

In `gradle/mac-launcher-identity.gradle`, replace:

```groovy
    // Seed with the immutable SDK resource, not the already patched/signed output. This keeps
    // verification independent of signing bytes and makes rewriting idempotent and reproducible.
```

with:

```groovy
    // Seed with the compiled launcher (gradle/launcher.gradle), not the already patched/signed output. This keeps
    // verification independent of signing bytes and makes rewriting idempotent and reproducible.
```

In `.github/workflows/linux-packages.yml`, add `      - gradle/launcher.gradle` after both
`      - gradle/packaging-support.gradle` lines (the `push` and `pull_request` path filters), and change
`sudo apt-get install -y --no-install-recommends fakeroot rpm cpio binutils` to
`sudo apt-get install -y --no-install-recommends fakeroot rpm cpio binutils g++`.

- [ ] **Step 6: Build and verify the package (headless)**

Run: `./gradlew :jasper-app:packageApp :jasper-app:verifyPackage`
Expected (macOS): BUILD SUCCESSFUL. `verifyPackage` accepts `-Xmx2048m` and `-XX:+IgnoreUnrecognizedVMOptions` in
`Jasper.cfg`, finds Jasper's launcher, accepts the Mach-O identity and the bundle signature, reports the launcher's SDK
as the installed one, and logs `No AOT cache in this image`. This builds the image only; do **not** open it and do
**not** add `-Pjasper.aot=true`.

Run: `grep java-options jasper-app/build/packaging/image/Jasper.app/Contents/app/Jasper.cfg`
Expected: includes `java-options=-Xmx2048m` and `java-options=-XX:+IgnoreUnrecognizedVMOptions`.

- [ ] **Step 7: Update the documentation**

In `docs/packaging.md`:

1. After the paragraph ending `The portable Windows ZIP does not require\nWiX.` add the paragraph:

   ```markdown
   Packaging also compiles Jasper's own build of jpackage's launcher ([packaging/launcher](../packaging/launcher/README.md))
   with the host's C/C++ compiler: the Xcode command line tools on macOS, `gcc` and `g++` on Linux, and Visual Studio
   or its Build Tools with the C++ workload on Windows. `./gradlew check` builds and tests it too.
   ```

2. Change `sudo apt-get install fakeroot rpm cpio binutils` to `sudo apt-get install fakeroot rpm cpio binutils g++`.

3. Replace the body of `## macOS 26 window controls` (everything between that heading and `## Bundled plugins stay off
   the classpath`) with:

   ```markdown
   AppKit draws the macOS 26 window controls only for a process whose main executable was
   built against the macOS 26 SDK or later. Jasper's launcher is compiled with the installed
   Xcode SDK, so its build-version load command records that SDK; `verifyPackage` checks it
   and warns when the SDK predates macOS 26. Only the packaged app benefits:
   `./gradlew :jasper-app:run` and `installDist` launch the runtime's own `java` binary, which
   keeps the legacy controls.
   ```

4. In `## macOS Local Network access`, change `After the\nSDK-version update and before signing, \`packageApp\` now gives`
   to `Before\nsigning, \`packageApp\` now gives`, and `the selected SDK's original launcher bytes` to
   `Jasper's compiled launcher bytes`.

In `docs/app-maintenance.md`, at the end of the bullet list of `## Packaged runtime, bundled natives and the AOT cache`
(after the `**The AOT cache.**` bullet), add:

```markdown
- **Jasper's launcher.** `packageApp` builds the image with Jasper's own build of jpackage's launcher
  (`gradle/launcher.gradle`, [packaging/launcher](../packaging/launcher/README.md)), which merges the user's
  `jasper.vmoptions` over these options. A JBR update means re-vendoring its launcher sources and re-applying the
  hook lines; `verifyLauncherSources`, `testLauncherCore`, `testLauncherGuard` and `verifyLauncherImage` (all in
  `check`) show what changed.
- **Bundled JVM defaults.** `-Xmx2048m` and `-XX:+IgnoreUnrecognizedVMOptions` are in `applicationDefaultJvmArgs`; a
  user's `jasper.vmoptions` overrides them. AOT training and the start-up benchmark's isolated home give the launcher
  an empty options file, so a developer's own flags never shape the cache or the numbers.
```

In `AGENTS.md`, after the bullet that starts ``- `./gradlew check` runs everything (headless).`` add:

```markdown
- `check` also builds Jasper's jpackage launcher from `packaging/launcher` and runs its native tests, so it needs the host's C/C++ compiler: the Xcode command line tools, `gcc`/`g++`, or MSVC (found through `vswhere`). AOT training gives the launcher an empty `jasper.vmoptions`. See [the launcher README](packaging/launcher/README.md).
```

In `docs/superpowers/specs/2026-10-13-jasper-custom-vm-options-design.md`, in the status paragraph, change
`Plan 1 (launcher) written:` to `Plan 1 (launcher) complete on <today's date>:`.

In this plan, change `Status: **Not started.**` to `Status: **Complete on <today's date>.**` followed by any
deviation beyond the six planned ones.

In `docs/STATUS.md`, add at the top (under the title), filling in the date and the counts from the final `check`:

```markdown
**Custom VM options plan 1 (launcher) complete — <date>.** On `claude/custom-vm-options` (not pushed): Jasper builds
jpackage's launcher from vendored JBR 25.0.4.1 sources (`packaging/launcher`) with marked hooks that merge the user's
`jasper.vmoptions` (location, format and IntelliJ's merge from the
[spec](superpowers/specs/2026-10-13-jasper-custom-vm-options-design.md)) and an exit guard that starts once more
without the user's options when the JVM rejects them. Bundled defaults `-Xmx2048m -XX:+IgnoreUnrecognizedVMOptions`.
`check` builds the launcher and runs its native tests, a guard test against the real libjli and a probe-image
end-to-end test on each OS. Deviations: the six in the plan's banner. Full `./gradlew check`: <tests> tests,
<failures> failures, <skips> skips. Manual checks pending (the user): the packaged app on macOS with a `jasper.vmoptions`
(`-Xmx3g`, then `-Xmx3q`), Windows and Linux packages. Plans 2 (settings and actions) and 3 (memory) are next.
```

- [ ] **Step 8: Run the whole check**

Run: `./gradlew check`
Expected: BUILD SUCCESSFUL. Read the counts with:

```bash
python3 - <<'PY'
import pathlib, xml.etree.ElementTree as ET
t = f = s = 0
for p in pathlib.Path('.').glob('*/build/test-results/test/*.xml'):
    r = ET.parse(p).getroot(); t += int(r.get('tests')); f += int(r.get('failures')) + int(r.get('errors')); s += int(r.get('skipped'))
print(t, 'tests', f, 'failures', s, 'skips')
PY
```

Put the counts into the `docs/STATUS.md` entry.

- [ ] **Step 9: Commit**

```bash
git add gradle jasper-app/build.gradle.kts jasper-app/src .github/workflows/linux-packages.yml docs AGENTS.md
git commit -F - <<'EOF'
Package Jasper with its own launcher and bundled VM defaults

packageApp, the Windows installer and the Linux packages hand jpackage
Jasper's launcher through -J--patch-module; verifyPackage checks it is
ours. The vtool SDK rewrite goes away (the launcher links against the
installed SDK) and the Mach-O identity seeds from the compiled launcher.
-Xmx2048m and -XX:+IgnoreUnrecognizedVMOptions join the bundled options.
AOT training and the benchmark's isolated home read an empty
jasper.vmoptions.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

## Hand-off checks for the user

Agents never launch the GUI. After the branch is reviewed, the user checks:

1. **macOS:** build with `./gradlew :jasper-app:packageApp`, open `jasper-app/build/packaging/image/Jasper.app`. Then
   write `-Xmx3g` into `~/.config/jasper/jasper.vmoptions`, quit and reopen: `jcmd <pid> VM.flags | tr ' ' '\n' | grep MaxHeapSize`
   shows 3 GiB. Change it to `-Xmx3q`, reopen: Jasper starts (once, no loop) with the defaults. Remove the file.
2. **macOS Local Network:** an SSH connection to a LAN host still works without a new permission prompt.
3. **Windows** (`.\gradlew.bat :jasper-app:packageInstaller` with MSVC and WiX): the exe keeps its icon and version
   information; the same two `jasper.vmoptions` checks under `%APPDATA%\jasper\`.
4. **Linux:** the `linux-packages` workflow builds and installs the `.deb` and `.rpm`; the same two checks under
   `~/.config/jasper/`.
