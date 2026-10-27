param(
    [ValidateSet('Installer', 'Dist')][string]$Package = 'Installer',
    [string]$Version,
    [string]$JavaHome,
    [string]$WixPath,
    [ValidateSet('x64', 'aarch64')][string]$Architecture
)

# Build the Windows setup wizard (or portable ZIP) with a JBR SDK 25 with JCEF of the target
# architecture and WiX 3. Gradle runs on that SDK, so the package matches it: by default this
# machine's own architecture. An x64 package on Windows on Arm runs entirely under emulation,
# JIT-compiled code included, so build one there only for x64 machines (-Architecture x64).
# Settings apply to this build only, not the calling shell.
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..')).Path

function Get-SdkArchitecture([string]$SdkHome) {
    $release = Join-Path $SdkHome 'release'
    if (-not (Test-Path -LiteralPath $release)) { return $null }
    $line = Select-String -LiteralPath $release -Pattern '^OS_ARCH="([^"]+)"' | Select-Object -First 1
    if ($line) { $line.Matches[0].Groups[1].Value } else { $null }
}

if (-not $Architecture) {
    $Architecture = if ([System.Runtime.InteropServices.RuntimeInformation]::OSArchitecture -eq 'Arm64') { 'aarch64' } else { 'x64' }
}
$architectureNames = if ($Architecture -eq 'aarch64') { @('aarch64', 'arm64') } else { @('x86_64', 'amd64', 'x64') }

# A JBR SDK 25 of the target architecture with jpackage and JCEF: the MODULES line of its release
# file names jcef as a whole module (jcef.something is another).
function Test-JbrSdk([string]$SdkHome) {
    (Get-SdkArchitecture $SdkHome) -in $architectureNames -and
        (Test-Path -LiteralPath (Join-Path $SdkHome 'bin\jpackage.exe')) -and
        (Select-String -LiteralPath (Join-Path $SdkHome 'release') -Pattern '^JAVA_VERSION="25' -Quiet) -and
        (Select-String -LiteralPath (Join-Path $SdkHome 'release') -Pattern '^IMPLEMENTOR="JetBrains' -Quiet) -and
        (Select-String -LiteralPath (Join-Path $SdkHome 'release') -Pattern '^MODULES="(.* )?jcef( .*)?"' -Quiet)
}

if (-not $JavaHome) {
    $candidates = @($env:JAVA_HOME) + @(
        Get-ChildItem -Path (Join-Path $env:USERPROFILE '.jdks'), (Join-Path $env:USERPROFILE '.gradle\jdks') `
            -Directory -Filter 'jbrsdk*' -ErrorAction SilentlyContinue |
            Sort-Object Name -Descending | ForEach-Object FullName)
    $JavaHome = $candidates | Where-Object { $_ -and (Test-JbrSdk $_) } | Select-Object -First 1
    if (-not $JavaHome) {
        throw "No $Architecture JBR SDK 25 with JCEF found in JAVA_HOME, ~\.jdks or ~\.gradle\jdks; pass -JavaHome"
    }
} elseif (-not (Test-JbrSdk $JavaHome)) {
    throw "$JavaHome is not a $Architecture JBR SDK 25 with JCEF and jpackage (OS_ARCH: $(Get-SdkArchitecture $JavaHome))"
}

if ($Package -eq 'Installer') {
    if (-not $WixPath) {
        $onPath = Get-Command candle.exe -ErrorAction SilentlyContinue
        $WixPath = if ($onPath) { Split-Path -Parent $onPath.Source } else {
            @((Join-Path $env:LOCALAPPDATA 'tauri\WixTools314')) + @(
                Get-ChildItem -Path ${env:ProgramFiles(x86)} -Directory -Filter 'WiX Toolset v3*' -ErrorAction SilentlyContinue |
                    ForEach-Object { Join-Path $_.FullName 'bin' }) |
                Where-Object { Test-Path -LiteralPath (Join-Path $_ 'candle.exe') } | Select-Object -First 1
        }
    }
    if (-not $WixPath -or -not (Test-Path -LiteralPath (Join-Path $WixPath 'light.exe'))) {
        throw 'WiX 3 (candle.exe and light.exe) not found; install WiX 3.14 or pass -WixPath'
    }
}

$task = if ($Package -eq 'Installer') { ':jasper-app:packageInstaller' } else { ':jasper-app:packageDist' }
$arguments = @(
    "-Dorg.gradle.java.installations.paths=$JavaHome",
    '-Dorg.gradle.java.installations.auto-detect=false',
    $task)
if ($Version) { $arguments = @("-PjasperVersion=$Version") + $arguments }

Write-Host "JBR SDK: $JavaHome ($Architecture)"
if ($WixPath) { Write-Host "WiX:     $WixPath" }
Write-Host "Task:    $task"

$savedJavaHome = $env:JAVA_HOME
$savedPath = $env:PATH
try {
    $env:JAVA_HOME = $JavaHome
    $env:PATH = (@($WixPath, (Join-Path $JavaHome 'bin'), $savedPath) | Where-Object { $_ }) -join ';'
    & (Join-Path $root 'gradlew.bat') -p $root @arguments
    if ($LASTEXITCODE -ne 0) { throw "Gradle failed with exit code $LASTEXITCODE" }
} finally {
    $env:JAVA_HOME = $savedJavaHome
    $env:PATH = $savedPath
}
