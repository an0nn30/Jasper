# Headless Windows integration tests: temporary files, shortcuts and a private HKCU key only.
. "$PSScriptRoot\Install-User.ps1"

function Assert([bool]$Condition, [string]$Message) {
    if (!$Condition) { throw $Message }
}
function Expect-Failure([scriptblock]$Operation, [string]$Message) {
    $failed = $false
    try { & $Operation } catch { $failed = $true }
    Assert $failed $Message
}

$root = Join-Path ([IO.Path]::GetTempPath()) "Jasper install test $([Guid]::NewGuid())"
$key = "HKCU:\Software\JasperInstallTest-$([Guid]::NewGuid())"
$source = Join-Path $root 'image'
$directory = Join-Path $root 'JasperApp'
$shortcut = Join-Path $root 'Programs\Jasper.lnk'
$data = Join-Path $root 'jasper\config.toml'
try {
    foreach ($file in @('Jasper.exe', 'app\Jasper.cfg', 'runtime\bin\java.exe', 'app\plugins\sample\sample.jar')) {
        $path = Join-Path $source $file
        [void][IO.Directory]::CreateDirectory((Split-Path -Parent $path))
        [IO.File]::WriteAllText($path, 'fixture')
    }
    [void][IO.Directory]::CreateDirectory((Split-Path -Parent $data))
    [IO.File]::WriteAllText($data, 'user data')
    [void](New-Item -Path $key -Force)

    Install-JasperUser $source $directory $shortcut
    Assert ((Get-ShortcutTarget $shortcut) -eq (Join-Path $directory 'Jasper.exe')) 'Wrong shortcut target'
    Assert (Test-Path -LiteralPath (Join-Path $directory 'app\plugins\sample\sample.jar')) 'Plugins missing'
    [IO.File]::WriteAllText((Join-Path $source 'Jasper.exe'), 'updated')
    Install-JasperUser $source $directory $shortcut
    Assert ([IO.File]::ReadAllText((Join-Path $directory 'Jasper.exe')) -eq 'updated') 'Update failed'

    # A failure while creating the shortcut must restore the previous installation.
    [IO.File]::WriteAllText((Join-Path $source 'Jasper.exe'), 'should roll back')
    $blockedParent = Join-Path $root 'not a directory'
    [IO.File]::WriteAllText($blockedParent, 'block')
    Expect-Failure { Install-JasperUser $source $directory (Join-Path $blockedParent 'Jasper.lnk') } 'Expected shortcut failure'
    Assert ([IO.File]::ReadAllText((Join-Path $directory 'Jasper.exe')) -eq 'updated') 'Update rollback failed'

    # A shortcut that cannot be rewritten must not prevent restoration of the old app.
    $lockedShortcut = [IO.File]::Open($shortcut, [IO.FileMode]::Open, [IO.FileAccess]::Read, [IO.FileShare]::Read)
    try {
        Expect-Failure { Install-JasperUser $source $directory $shortcut } 'Expected locked shortcut failure'
        Assert ([IO.File]::ReadAllText((Join-Path $directory 'Jasper.exe')) -eq 'updated') 'Locked shortcut prevented app rollback'
    } finally { $lockedShortcut.Dispose() }

    # A foreign login entry survives, as does all user data.
    [void](New-ItemProperty -Path $key -Name Jasper -Value '"C:\Other\Jasper.exe" --background')
    Uninstall-JasperUser $directory $shortcut $key
    Assert (!(Test-Path -LiteralPath $directory)) 'Installation remains'
    Assert (!(Test-Path -LiteralPath $shortcut)) 'Shortcut remains'
    Assert ((Get-ItemProperty -Path $key).Jasper -eq '"C:\Other\Jasper.exe" --background') 'Foreign autostart removed'
    Assert ([IO.File]::ReadAllText($data) -eq 'user data') 'User data modified'
    Uninstall-JasperUser $directory $shortcut $key

    Install-JasperUser $source $directory $shortcut
    Set-ItemProperty -Path $key -Name Jasper -Value "`"$(Join-Path $directory 'Jasper.exe')`" --background"
    Uninstall-JasperUser $directory $shortcut $key
    Assert ($null -eq (Get-ItemProperty -Path $key -Name Jasper -ErrorAction SilentlyContinue)) 'Owned autostart remains'

    [void][IO.Directory]::CreateDirectory($directory)
    [IO.File]::WriteAllText((Join-Path $directory 'keep.txt'), 'foreign files')
    Expect-Failure { Install-JasperUser $source $directory $shortcut } 'Overwrote unowned directory'
    Expect-Failure { Uninstall-JasperUser $directory $shortcut $key } 'Removed unowned directory'
    Assert (Test-Path -LiteralPath (Join-Path $directory 'keep.txt')) 'Foreign file lost'
    Remove-Item -LiteralPath $directory -Recurse -Force

    # Directory junctions do not require developer mode or administrator rights.
    [void](New-Item -ItemType Junction -Path $directory -Target (Split-Path -Parent $data))
    try {
        Expect-Failure { Install-JasperUser $source $directory $shortcut } 'Followed linked installation'
        Expect-Failure { Uninstall-JasperUser $directory $shortcut $key } 'Removed linked installation'
        Assert ([IO.File]::ReadAllText($data) -eq 'user data') 'Linked user data modified'
    } finally {
        [IO.Directory]::Delete($directory)
    }

    # Another installation's shortcut cannot be replaced.
    $shell = New-Object -ComObject WScript.Shell
    try {
        $link = $shell.CreateShortcut($shortcut)
        $link.TargetPath = 'C:\Other\Jasper.exe'
        $link.Save()
    } finally { [void][Runtime.InteropServices.Marshal]::FinalReleaseComObject($shell) }
    Expect-Failure { Install-JasperUser $source $directory $shortcut } 'Replaced foreign shortcut'
    Uninstall-JasperUser $directory $shortcut $key
    Assert ((Get-ShortcutTarget $shortcut) -eq 'C:\Other\Jasper.exe') 'Removed foreign shortcut'
    Write-Host 'Windows user install tests passed.'
} finally {
    if (Test-Path -LiteralPath $root) { Remove-Item -LiteralPath $root -Recurse -Force }
    if (Test-Path -LiteralPath $key) { Remove-Item -LiteralPath $key -Recurse -Force }
}
