param(
    [ValidateSet('Install', 'Uninstall')][string]$Action,
    [string]$Image
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

# Keep the installation separate from %APPDATA%\jasper, which contains the user's data.
$script:InstallMarker = '.jasper-user-install'
$script:InstallIdentity = 'Jasper standard-launcher user installation v1'

function Assert-PlainTree([string]$Path) {
    if (!(Test-Path -LiteralPath $Path)) { return }
    $item = Get-Item -LiteralPath $Path -Force
    if ($item.Attributes -band [IO.FileAttributes]::ReparsePoint) {
        throw "Refusing to modify a linked installation: $Path"
    }
    if ($item.PSIsContainer) {
        foreach ($child in Get-ChildItem -LiteralPath $Path -Force) {
            Assert-PlainTree $child.FullName
        }
    }
}

function Assert-OwnedInstall([string]$Directory) {
    if (!(Test-Path -LiteralPath $Directory)) { return }
    Assert-PlainTree $Directory
    $marker = Join-Path $Directory $script:InstallMarker
    if (!(Test-Path -LiteralPath $marker -PathType Leaf) -or
        [IO.File]::ReadAllText($marker) -ne $script:InstallIdentity) {
        throw "Refusing to replace or remove an unrecognized directory: $Directory"
    }
}

function Assert-JasperStopped([string]$Directory) {
    $executable = Join-Path $Directory 'Jasper.exe'
    foreach ($process in Get-Process -Name Jasper -ErrorAction SilentlyContinue) {
        # A process whose executable cannot be inspected is not safe to replace.
        if (!$process.Path -or $process.Path -eq $executable) {
            throw 'Exit Jasper (including its background process) before installing or uninstalling.'
        }
    }
}

function Get-ShortcutTarget([string]$Shortcut) {
    if (!(Test-Path -LiteralPath $Shortcut)) { return $null }
    Assert-PlainTree $Shortcut
    $shell = New-Object -ComObject WScript.Shell
    try { return $shell.CreateShortcut($Shortcut).TargetPath }
    finally { [void][Runtime.InteropServices.Marshal]::FinalReleaseComObject($shell) }
}

function Install-JasperUser([string]$Source, [string]$Directory, [string]$Shortcut) {
    foreach ($file in @('Jasper.exe', 'app\Jasper.cfg', 'runtime\bin\java.exe')) {
        if (!(Test-Path -LiteralPath (Join-Path $Source $file) -PathType Leaf)) {
            throw "Incomplete Jasper app image: missing $file"
        }
    }
    Assert-PlainTree $Source
    Assert-OwnedInstall $Directory
    Assert-JasperStopped $Directory
    $target = Join-Path $Directory 'Jasper.exe'
    $oldTarget = Get-ShortcutTarget $Shortcut
    if ((Test-Path -LiteralPath $Shortcut) -and $oldTarget -ne $target) {
        throw "Another installation owns $Shortcut"
    }
    $stage = "$Directory.staging-$([Guid]::NewGuid())"
    $backup = "$Directory.backup-$([Guid]::NewGuid())"
    $shortcutBackup = $null
    if (Test-Path -LiteralPath $Shortcut) { $shortcutBackup = [IO.File]::ReadAllBytes($Shortcut) }
    $installed = $false
    try {
        [void](New-Item -ItemType Directory -Path $stage)
        Get-ChildItem -LiteralPath $Source -Force | Copy-Item -Destination $stage -Recurse -Force
        [IO.File]::WriteAllText((Join-Path $stage $script:InstallMarker), $script:InstallIdentity)
        if (Test-Path -LiteralPath $Directory) { Move-Item -LiteralPath $Directory -Destination $backup }
        Move-Item -LiteralPath $stage -Destination $Directory
        $installed = $true
        [void][IO.Directory]::CreateDirectory((Split-Path -Parent $Shortcut))
        $shell = New-Object -ComObject WScript.Shell
        try {
            $link = $shell.CreateShortcut($Shortcut)
            $link.TargetPath = $target
            $link.Arguments = ''
            $link.WorkingDirectory = $Directory
            $link.IconLocation = "$target,0"
            $link.Description = 'Jasper terminal'
            $link.Save()
        } finally { [void][Runtime.InteropServices.Marshal]::FinalReleaseComObject($shell) }
    } catch {
        if ($installed) {
            Remove-Item -LiteralPath $Directory -Recurse -Force
        }
        # Restore the app first: a locked/read-only shortcut must not prevent app rollback.
        if (Test-Path -LiteralPath $backup) { Move-Item -LiteralPath $backup -Destination $Directory }
        if ($installed) {
            if ($null -ne $shortcutBackup) { [IO.File]::WriteAllBytes($Shortcut, $shortcutBackup) }
            elseif (Test-Path -LiteralPath $Shortcut) { Remove-Item -LiteralPath $Shortcut -Force }
        }
        throw
    } finally {
        if (Test-Path -LiteralPath $stage) { Remove-Item -LiteralPath $stage -Recurse -Force }
    }
    if (Test-Path -LiteralPath $backup) { Remove-Item -LiteralPath $backup -Recurse -Force }
    Write-Host "Installed Jasper at $Directory. Open Jasper from the Start Menu; its background setting controls residency and login startup."
}

function Uninstall-JasperUser([string]$Directory, [string]$Shortcut, [string]$RunKey) {
    Assert-OwnedInstall $Directory
    Assert-JasperStopped $Directory
    $target = Join-Path $Directory 'Jasper.exe'
    if ((Get-ShortcutTarget $Shortcut) -eq $target) { Remove-Item -LiteralPath $Shortcut -Force }
    # Remove only the login entry for this installation; another Jasper installation may own it.
    $entry = Get-ItemProperty -LiteralPath $RunKey -Name Jasper -ErrorAction SilentlyContinue
    if ($null -ne $entry -and $entry.Jasper -eq "`"$target`" --background") {
        Remove-ItemProperty -LiteralPath $RunKey -Name Jasper
    }
    if (Test-Path -LiteralPath $Directory) { Remove-Item -LiteralPath $Directory -Recurse -Force }
    Write-Host 'Uninstalled Jasper. User settings, plugins and data are preserved.'
}

if ($MyInvocation.InvocationName -ne '.') {
    if (!$Action) { throw 'Specify -Action Install or -Action Uninstall.' }
    $appData = [Environment]::GetFolderPath('ApplicationData')
    $programs = [Environment]::GetFolderPath('Programs')
    if (!$appData -or !$programs) { throw 'Windows user application-data and Start Menu folders are required.' }
    $directory = Join-Path $appData 'JasperApp'
    $shortcut = Join-Path $programs 'Jasper.lnk'
    if ($Action -eq 'Install') { Install-JasperUser $Image $directory $shortcut }
    else { Uninstall-JasperUser $directory $shortcut 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Run' }
}
