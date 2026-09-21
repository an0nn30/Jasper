# Explicit native invocation. Caller first checks the no-game/no-VM prerequisite.
param(
    [Parameter(Mandatory=$true)][string]$Image,
    [Parameter(Mandatory=$true)][ValidateSet('throughput','memory')][string]$Mode,
    [Parameter(Mandatory=$true)][string]$Revision,
    [Parameter(Mandatory=$true)][string]$Output,
    [Parameter(ValueFromRemainingArguments=$true)][string[]]$BenchmarkArguments
)
$ErrorActionPreference = 'Stop'
# Compatible with Windows PowerShell 5.1 as well as PowerShell 7.
if ($Image -notmatch '^(?:[A-Za-z]:[\\/]|\\\\)' -or $Output -notmatch '^(?:[A-Za-z]:[\\/]|\\\\)') {
    throw 'Use absolute package and output paths'
}
$entry = if ($Mode -eq 'throughput') { 'Bench' } else { 'MemoryBench' }
# Inspect the archive without launching Java or touching the desktop.
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [System.IO.Compression.ZipFile]::OpenRead((Join-Path $Image 'app\jasper-app.jar'))
try {
    $entryPackage = if ($null -ne $archive.GetEntry("dev/jasper/app/benchmark/$entry.class")) {
        'dev.jasper.app.benchmark'
    } else { 'dev.jasper.app' }
} finally { $archive.Dispose() }
& (Join-Path $Image 'runtime\bin\java.exe') '--enable-native-access=ALL-UNNAMED' '-cp' (Join-Path $Image 'app\*') `
    "$entryPackage.$entry" '--revision' $Revision '--output' $Output @BenchmarkArguments
exit $LASTEXITCODE
