# macOS Local Network identity fix — 2026-09-27

## Confirmed failure

The installed Jasper already had `NSLocalNetworkUsageDescription` and a valid ad-hoc signature.
During the user's Remote SSH retry, macOS `nehelper` logged at 00:13:37.420 local time:

```
Local network denied by preference for Moray (dev.moray.app)
```

Jasper, historical Moray, other diagnostic apps and the original JBR jpackage launcher all used
Mach-O UUID `25BA8067-AB3A-327B-8BE9-0C3162B915F5`. Apple's TN3178 and TN3179 document that sharing
main-executable UUIDs across different app identities can mix up network permissions.

## Correction

After updating the launcher's SDK load command, packaging replaces only the 16 UUID bytes,
then re-signs the bundle. The UUID is derived from an explicit domain, the bundle identifier
and the original SDK launcher resource. Repeated identical inputs remain reproducible. The
parser accepts the supported thin arm64/x86_64 executable formats and fails on malformed
headers, invalid command bounds, or missing/duplicate UUIDs.

The verification task recomputes the expected identity from the immutable SDK resource,
independently of the packaged executable's current UUID and signature bytes.
Apple-issued signing and notarization remain separate release work.

## Verification

- Clean baseline `./gradlew check`: passed.
- New portable regression initially failed: `Packaged app retains the generic launcher UUID`.
- Fixed regression: passed for app/architecture/code isolation, repeatability, idempotence,
  preservation of unrelated bytes and rejection of malformed binaries.
- Final `./gradlew check`: passed; XML totals **1,997 tests, zero failures/errors, three skips**.
  The portable UUID task is additional to the JUnit XML counts.
- `./gradlew :jasper-app:packageDist`: passed.
- Resulting UUID: `7BE1B61B-E8A0-8C0F-A10E-A8FA91D81F38` with the current JBR SDK.
- Replaced the build image UUID with the old generic value: `verifyPackage -x packageApp`
  rejected it with the app-specific UUID diagnostic. Restored original bytes in `finally`;
  normal `verifyPackage` then passed.
- Mounted the DMG read-only without opening Finder: its UUID matches the corrected image and
  `codesign --verify --deep --strict` passes. An initial whole-file byte comparison differed;
  inspection confirmed the only 32 differing bytes are inside the code-signature blob added
  during DMG packaging, with identical launcher code/load commands outside that blob.
- Independent review found no actionable issues and independently checked UUID derivation
  and strict signature validation.
- No installed app changes, GUI launches, network permission changes, merge or push.

## User acceptance still required

1. Fully quit Jasper, including any background process.
2. Install `jasper-app/build/packaging/dist/Jasper-1.0.0-macos-aarch64.dmg` from this branch.
3. Launch Jasper from Finder and connect Remote to the LAN host.
4. Allow Jasper when macOS prompts; retry the connection if necessary.
5. Confirm Jasper appears in Privacy & Security → Local Network and that SSH connects.

If the prompt still fails, correlate another retry with macOS network logs. Ad-hoc signing
and multiple registered installations remain potential sources of OS identity problems.
Do not treat this headless verification as proof that the native permission prompt works.
