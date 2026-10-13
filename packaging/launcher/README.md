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
