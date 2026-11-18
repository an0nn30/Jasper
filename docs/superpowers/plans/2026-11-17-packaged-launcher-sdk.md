# Packaged launcher SDK invalidation implementation plan

> **For agentic workers:** Use superpowers:subagent-driven-development to implement and review this task.

**Status:** In progress on `codex/fix-packaged-launcher-sdk`, from `main` `7e3e0ca64`.
The existing checkout is reused to reproduce its stale native output. No implementation deviations yet.

**Goal:** Rebuild the macOS launcher when the selected SDK changes, then deliver a verified app image.

**Architecture:** Gradle's launcher inputs include the SDK selected by `xcrun`, alongside its existing Java SDK and host inputs. Package verification keeps requiring the launcher to record the installed SDK.

**Tech stack:** Gradle Groovy scripts, Xcode, JBR 25.0.4.1 b635.75 JCEF.

**Spec:** [Packaging build contract](../specs/2026-09-12-jasper-plan-4d-packaging-design.md#build-contract), with the current [launcher build](../../../packaging/launcher/README.md).

## Global constraints

- Reuse the current checkout on a branch; do not merge or push.
- Use `./gradlew`; package verification remains headless.
- AOT training requires user approval before it opens its short-lived window.
- Keep other platforms free of macOS tool invocations.
- End commits with a `Co-Authored-By:` trailer.

## Review focus

- An Xcode upgrade at the same path must invalidate the launcher through the SDK version.
- Selecting another SDK path must invalidate the launcher even if its version matches.
- Linux and Windows builds must not run `xcrun`.
- Non-native task configuration must not eagerly require Xcode.
- An unchanged SDK must keep the launcher up to date.

## Task 1: Track the selected macOS SDK

**Files:** `gradle/launcher.gradle`, `gradle/packaging-support-test.gradle`, `packaging/launcher/README.md`.

- [ ] Add this regression block to `verifySupport` in `gradle/packaging-support-test.gradle`:

```groovy
verifySupport.configure {
    doLast {
        Map inputs = tasks.named('buildLauncher').get().inputs.properties
        if (System.getProperty('os.name').toLowerCase(Locale.ROOT).startsWith('mac')) {
            ['macSdkPath': '--show-sdk-path', 'macSdkVersion': '--show-sdk-version'].each { name, option ->
                String expected = providers.exec {
                    commandLine('/usr/bin/xcrun', option)
                }.standardOutput.asText.get().trim()
                assert inputs[name] == expected : "buildLauncher must track ${name}"
            }
        } else {
            assert !inputs.containsKey('macSdkPath') && !inputs.containsKey('macSdkVersion')
        }
        logger.lifecycle('Verified launcher macOS SDK inputs')
    }
}
```

- [ ] Run `./gradlew :jasper-app:verifyPackagingSupport --console=plain`; observe the missing SDK input failure.
- [ ] Add the following after the existing `host` input in **buildLauncher only**:

```groovy
    // Xcode can replace its SDK in place; both its selected path and version invalidate the launcher.
    if (launcherHost().os == 'macos') {
        inputs.property('macSdkPath', providers.exec {
            commandLine('/usr/bin/xcrun', '--show-sdk-path')
        }.standardOutput.asText.map { it.trim() })
        inputs.property('macSdkVersion', providers.exec {
            commandLine('/usr/bin/xcrun', '--show-sdk-version')
        }.standardOutput.asText.map { it.trim() })
    }
```

- [ ] Add to the launcher README's build paragraph: "On macOS, the selected SDK path and version are task inputs, so an Xcode SDK update rebuilds the launcher."
- [ ] Run `./gradlew :jasper-app:verifyPackagingSupport :jasper-app:buildLauncher --console=plain`; inspect the binary with `xcrun vtool -show-build`, requiring SDK 27.2 on this host. Repeat `buildLauncher`, expecting UP-TO-DATE.
- [ ] Commit the fix and regression with the required trailer; obtain task review.
- [ ] Controller: run the full headless `check` with Python 3.11–3.13, `:jasper-app:verifyPackage`, and final branch review. Record evidence and deviations here and in `docs/STATUS.md`.
- [ ] Controller: obtain the required AOT approval after the headless image is concrete, then run `:jasper-app:packageDist` and deliver its verified DMG and app image. If AOT is declined, deliver the verified headless app image.
