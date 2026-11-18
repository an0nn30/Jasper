#!/bin/sh
# Uses the repository wrapper and configured JAVA_HOME; no application task or GUI.
set -eu
root=$(CDPATH= cd -- "$(dirname -- "$0")/../../.." && pwd)
fixture=$(mktemp -d "${TMPDIR:-/tmp}/jasper-offline-init.XXXXXX")
trap 'rm -rf "$fixture"' EXIT HUP INT TERM
cp -R "$root/packaging/gentoo/tests/offline-init/." "$fixture/"
export JASPER_MAVEN_REPO="$fixture/maven"
artifact="$JASPER_MAVEN_REPO/dev/jasper/gentoo/fixture/artifact/1.0"
parent="$JASPER_MAVEN_REPO/dev/jasper/gentoo/fixture/parent/1.0"
mkdir -p "$artifact" "$parent"
printf '%s\n' offline-artifact > "$artifact/artifact-1.0.jar"
cat > "$parent/parent-1.0.pom" <<'POM'
<project><modelVersion>4.0.0</modelVersion><groupId>dev.jasper.gentoo.fixture</groupId><artifactId>parent</artifactId><version>1.0</version><packaging>pom</packaging></project>
POM
cat > "$artifact/artifact-1.0.pom" <<'POM'
<project><modelVersion>4.0.0</modelVersion><parent><groupId>dev.jasper.gentoo.fixture</groupId><artifactId>parent</artifactId><version>1.0</version></parent><artifactId>artifact</artifactId></project>
POM
"$root/gradlew" --offline --no-daemon --no-build-cache --console=plain \
    -p "$fixture" -I "${1:-$root/packaging/gentoo/app-misc/jasper-workstation/files/offline.gradle}" \
    verifyOfflineRepository
