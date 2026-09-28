param(
    [ValidateSet('Installer', 'Dist')][string]$Package = 'Installer',
    [string]$Version,
    [string]$JavaHome,
    [string]$WixPath
)

# Build the Windows setup wizard (or portable ZIP) with an x64 JBR SDK 25 and WiX 3.
# Gradle itself must run on the x64 SDK: on Windows ARM64 it runs under emulation and
# still produces an x64 package. Settings apply to this build only, not the calling shell.
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..')).Path

function Get-SdkArchitecture([string]$SdkHome) {
    $release = Join-Path $SdkHome 'release'
    if (-not (Test-Path -LiteralPath $release)) { return $null }
    $line = Select-String -LiteralPath $release -Pattern '^OS_ARCH="([^"]+)"' | Select-Object -First 1
    if ($line) { $line.Matches[0].Groups[1].Value } else { $null }
}

function Test-X64JbrSdk([string]$SdkHome) {
    (Get-SdkArchitecture $SdkHome) -in @('x86_64', 'amd64', 'x64') -and
        (Test-Path -LiteralPath (Join-Path $SdkHome 'bin\jpackage.exe')) -and
        (Select-String -LiteralPath (Join-Path $SdkHome 'release') -Pattern '^JAVA_VERSION="25' -Quiet) -and
        (Select-String -LiteralPath (Join-Path $SdkHome 'release') -Pattern '^IMPLEMENTOR="JetBrains' -Quiet)
}

if (-not $JavaHome) {
    $candidates = @($env:JAVA_HOME) + @(
        Get-ChildItem -Path (Join-Path $env:USERPROFILE '.jdks'), (Join-Path $env:USERPROFILE '.gradle\jdks') `
            -Directory -Filter 'jbrsdk*' -ErrorAction SilentlyContinue |
            Sort-Object Name -Descending | ForEach-Object FullName)
    $JavaHome = $candidates | Where-Object { $_ -and (Test-X64JbrSdk $_) } | Select-Object -First 1
    if (-not $JavaHome) {
        throw 'No x64 JBR SDK 25 found in JAVA_HOME, ~\.jdks or ~\.gradle\jdks; pass -JavaHome'
    }
} elseif (-not (Test-X64JbrSdk $JavaHome)) {
    throw "$JavaHome is not an x64 JBR SDK 25 with jpackage (OS_ARCH: $(Get-SdkArchitecture $JavaHome))"
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

Write-Host "JBR SDK: $JavaHome"
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
