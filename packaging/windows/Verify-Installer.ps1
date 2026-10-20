param(
    [Parameter(Mandatory = $true)][string]$MsiPath,
    [Parameter(Mandatory = $true)][string]$Version,
    [Parameter(Mandatory = $true)][guid]$UpgradeUuid
)

# Read the MSI database without installing anything or starting Jasper.
$ErrorActionPreference = 'Stop'
$installer = New-Object -ComObject WindowsInstaller.Installer
$database = $null
try {
    $database = $installer.OpenDatabase((Resolve-Path -LiteralPath $MsiPath).Path, 0)
    function Read-Column([string]$Query) {
        $view = $database.OpenView($Query)
        try {
            [void]$view.Execute()
            while ($record = $view.Fetch()) {
                try { $record.StringData(1) }
                finally { [void][Runtime.InteropServices.Marshal]::FinalReleaseComObject($record) }
            }
        } finally {
            [void]$view.Close()
            [void][Runtime.InteropServices.Marshal]::FinalReleaseComObject($view)
        }
    }

    $name = Read-Column 'SELECT `Value` FROM `Property` WHERE `Property` = ''ProductName'''
    $actualVersion = Read-Column 'SELECT `Value` FROM `Property` WHERE `Property` = ''ProductVersion'''
    $upgrade = Read-Column 'SELECT `Value` FROM `Property` WHERE `Property` = ''UpgradeCode'''
    if ($name -ne 'Jasper' -or $actualVersion -ne $Version -or [guid]$upgrade -ne $UpgradeUuid) {
        throw "Unexpected installer identity: $name / $actualVersion / $upgrade"
    }
    $allUsers = Read-Column 'SELECT `Value` FROM `Property` WHERE `Property` = ''ALLUSERS'''
    if ($allUsers) { throw "Installer must be per-user, but ALLUSERS=$allUsers" }
    $summary = $database.SummaryInformation(0)
    try {
        # jpackage's WiX template always declares x64, for an ARM64 payload too (Windows on Arm installs it);
        # verifyPackage checks the binaries' own architecture.
        if ($summary.Property(7) -notmatch '^x64;') { throw 'Installer must use the x64 Windows Installer platform' }
    } finally { [void][Runtime.InteropServices.Marshal]::FinalReleaseComObject($summary) }

    $dialogs = @(Read-Column 'SELECT `Dialog` FROM `Dialog`')
    foreach ($dialog in @('InstallDirDlg', 'ShortcutPromptDlg')) {
        if ($dialogs -notcontains $dialog) { throw "Missing wizard dialog: $dialog" }
    }
    $shortcuts = @(Read-Column 'SELECT `Directory_` FROM `Shortcut`')
    if ($shortcuts.Count -lt 2) { throw 'Missing Start menu or desktop shortcut' }
    $upgrades = @(Read-Column 'SELECT `UpgradeCode` FROM `Upgrade`')
    if ($upgrades.Count -eq 0 -or ($upgrades | Where-Object { [guid]$_ -ne $UpgradeUuid })) {
        throw 'Missing or inconsistent upgrade detection'
    }
    $actions = @(Read-Column 'SELECT `Action` FROM `InstallExecuteSequence`')
    if ($actions -notcontains 'RemoveExistingProducts') { throw 'Missing upgrade removal action' }
    Write-Output "Verified Jasper $Version per-user setup wizard and upgrade identity $UpgradeUuid"
} finally {
    if ($null -ne $database) { [void][Runtime.InteropServices.Marshal]::FinalReleaseComObject($database) }
    [void][Runtime.InteropServices.Marshal]::FinalReleaseComObject($installer)
}
