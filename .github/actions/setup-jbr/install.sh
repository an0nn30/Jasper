#!/bin/bash
# setup-java's JetBrains resolver requires SemVer; JBR 25.0.4.1 has four components.
# The existing manifest pins the full SDK/JCEF bundle, including macOS Frameworks.
set -euo pipefail

case "$RUNNER_OS" in
  Linux) platform=linux ;;
  macOS) platform=osx ;;
  Windows) platform=windows ;;
  *) echo "Unsupported JBR runner OS: $RUNNER_OS" >&2; exit 1 ;;
esac
case "$RUNNER_ARCH" in
  X64) architecture=x64 ;;
  ARM64) architecture=aarch64 ;;
  *) echo "Unsupported JBR runner architecture: $RUNNER_ARCH" >&2; exit 1 ;;
esac

workspace=$GITHUB_WORKSPACE
runner_temp=$RUNNER_TEMP
environment_file=$GITHUB_ENV
path_file=$GITHUB_PATH
if [[ "$platform" == windows ]]; then
  workspace=$(cygpath -u "$workspace")
  runner_temp=$(cygpath -u "$runner_temp")
  environment_file=$(cygpath -u "$environment_file")
  path_file=$(cygpath -u "$path_file")
fi

# Require exactly one archive for this platform; runtime bumps update the manifest.
archive=
checksum=
while read -r digest filename; do
  case "$filename" in
    jbrsdk_jcef-*-$platform-$architecture-b*.tar.gz)
      if [[ -n "$archive" ]]; then
        echo "Multiple pinned JBR archives for $platform/$architecture" >&2; exit 1
      fi
      archive=$filename
      checksum=$digest
      ;;
  esac
done < "$workspace/packaging/jcef/checksums.txt"
if [[ -z "$archive" ]]; then
  echo "No pinned JBR archive for $platform/$architecture" >&2; exit 1
fi

installation=$(mktemp -d "$runner_temp/jasper-jbr.XXXXXX")
download="$installation/$archive"
curl --fail --location --retry 3 --output "$download" \
  "https://cache-redirector.jetbrains.com/intellij-jbr/$archive"
# Verify before extraction, using the checked-in SHA-512 rather than a remote checksum.
if [[ "$platform" == osx ]]; then
  (cd "$installation" && printf '%s  %s\n' "$checksum" "$archive" | shasum -a 512 -c -)
else
  (cd "$installation" && printf '%s  %s\n' "$checksum" "$archive" | sha512sum -c -)
fi
mkdir "$installation/sdk"
tar -xzf "$download" --strip-components=1 -C "$installation/sdk"
rm "$download"
java_home="$installation/sdk"
java_command=java
if [[ "$platform" == osx ]]; then
  java_home="$java_home/Contents/Home"
  test -d "$java_home/../Frameworks"
elif [[ "$platform" == windows ]]; then
  java_command=java.exe
fi
"$java_home/bin/$java_command" -version
if [[ "$platform" == windows ]]; then
  java_home=$(cygpath -w "$java_home")
fi
printf 'JAVA_HOME=%s\n' "$java_home" >> "$environment_file"
if [[ "$platform" == windows ]]; then
  printf '%s\\bin\n' "$java_home" >> "$path_file"
else
  printf '%s/bin\n' "$java_home" >> "$path_file"
fi
