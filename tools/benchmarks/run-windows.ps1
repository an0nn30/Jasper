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
& (Join-Path $Image 'runtime\bin\java.exe') '--enable-native-access=ALL-UNNAMED' '-cp' (Join-Path $Image 'app\*') `
    "dev.jasper.app.$entry" '--revision' $Revision '--output' $Output @BenchmarkArguments
exit $LASTEXITCODE
