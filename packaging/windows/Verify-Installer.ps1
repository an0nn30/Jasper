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
    # One string per row: the first column, or the first $Columns columns joined with '|' (a null field reads as '').
    function Read-Column([string]$Query, [int]$Columns = 1) {
        $view = $database.OpenView($Query)
        try {
            [void]$view.Execute()
            while ($record = $view.Fetch()) {
                try { $(foreach ($field in 1..$Columns) { $record.StringData($field) }) -join '|' }
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

    # The web browser registration: Root 1 is HKCU, a '-' name deletes that key and everything below it on uninstall.
    $attributes = Read-Column 'SELECT `Attributes` FROM `Component` WHERE `Component` = ''JasperWebBrowser'''
    $features = @(Read-Column 'SELECT `Feature_` FROM `FeatureComponents` WHERE `Component_` = ''JasperWebBrowser''')
    # msidbComponentAttributesPermanent (16) would leave the registration behind on uninstall.
    if ($null -eq $attributes -or ([int]$attributes -band 16) -or $features.Count -eq 0) {
        throw 'The web browser registration must be a feature component that the uninstall removes'
    }
    $registration = @(
        '1|Software\RegisteredApplications|Jasper|Software\Jasper\Capabilities',
        '1|Software\Jasper\Capabilities|-|',
        '1|Software\Jasper\Capabilities|ApplicationName|Jasper',
        '1|Software\Jasper\Capabilities|ApplicationDescription|Jasper''s built-in web browser',
        '1|Software\Jasper\Capabilities\URLAssociations|http|JasperURL',
        '1|Software\Jasper\Capabilities\URLAssociations|https|JasperURL',
        '1|Software\Classes\JasperURL|-|',
        '1|Software\Classes\JasperURL||Jasper URL',
        '1|Software\Classes\JasperURL\DefaultIcon||"[INSTALLDIR]Jasper.exe",0',
        '1|Software\Classes\JasperURL\shell\open\command||"[INSTALLDIR]Jasper.exe" --url "%1"',
        '1|Software\Classes\JasperFile|-|',
        '1|Software\Classes\JasperFile||Jasper document',
        '1|Software\Classes\JasperFile\DefaultIcon||"[INSTALLDIR]Jasper.exe",0',
        '1|Software\Classes\JasperFile\shell\open\command||"[INSTALLDIR]Jasper.exe" "%1"'
    )
    $rows = @(Read-Column 'SELECT `Root`, `Key`, `Name`, `Value` FROM `Registry` WHERE `Component_` = ''JasperWebBrowser''' 4)
    if ($rows.Count -ne $registration.Count -or @($registration | Where-Object { $rows -cnotcontains $_ }).Count -ne 0) {
        throw "Unexpected web browser registration: $($rows -join '; ')"
    }
    Write-Output "Verified Jasper $Version per-user setup wizard, upgrade identity $UpgradeUuid and web browser registration"
} finally {
    if ($null -ne $database) { [void][Runtime.InteropServices.Marshal]::FinalReleaseComObject($database) }
    [void][Runtime.InteropServices.Marshal]::FinalReleaseComObject($installer)
}
