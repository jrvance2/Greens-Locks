<#
.NAME
    Install-OktaVerify
.DESCRIPTION
    Silent SCCM/MECM install wrapper for Okta Verify for Windows.
    Place the Okta Verify installer (OktaVerifySetup-*.exe) in the same
    content folder as this script.

    SCCM install command:
      powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\Install-OktaVerify.ps1

    Exit codes are passed through to SCCM (0 = success, 3010 = soft reboot, etc).
#>
param(
    # Installer file name; defaults to the newest OktaVerifySetup-*.exe in the script folder
    [string]$Installer,
    # Optional extra installer properties, e.g. 'SKU=ALL ORGURL=https://org.okta.com CLIENTID=xxxx'
    [string]$InstallArgs = ''
)

$LogFolder = Join-Path $env:ProgramData 'OIT\SCCMLogs'
$null = New-Item -Path $LogFolder -ItemType Directory -Force -ErrorAction SilentlyContinue
$LogPath = Join-Path $LogFolder 'OktaVerify_Install.log'
$MsiLog  = Join-Path $LogFolder 'OktaVerify_Installer.log'

function Write-Log([string]$Message) {
    $line = '{0}  {1}' -f (Get-Date -Format 'yyyy-MM-dd HH:mm:ss'), $Message
    Add-Content -Path $LogPath -Value $line
    Write-Output $line
}

try {
    Write-Log '===== Okta Verify install started ====='

    if ($Installer) {
        $InstallerPath = Join-Path $PSScriptRoot $Installer
    } else {
        $InstallerPath = Get-ChildItem -Path $PSScriptRoot -Filter 'OktaVerifySetup*.exe' |
            Sort-Object Name -Descending | Select-Object -First 1 -ExpandProperty FullName
    }

    if (-not $InstallerPath -or -not (Test-Path $InstallerPath)) {
        Write-Log 'ERROR: Okta Verify installer not found in package content.'
        exit 2
    }

    # Close any running instance so files are not locked during upgrade
    Get-Process -Name 'OktaVerify' -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue

    $arguments = ('/q /norestart /log "{0}" {1}' -f $MsiLog, $InstallArgs).Trim()
    Write-Log "Running: `"$InstallerPath`" $arguments"

    $proc = Start-Process -FilePath $InstallerPath -ArgumentList $arguments -Wait -PassThru -WindowStyle Hidden
    $code = $proc.ExitCode
    Write-Log "Installer exit code: $code"

    if ($code -in 0, 3010, 1641) {
        Write-Log 'Install completed successfully.'
    } else {
        Write-Log "ERROR: Install failed. See $MsiLog"
    }
    exit $code
}
catch {
    Write-Log "ERROR: $($_.Exception.Message)"
    exit 1603
}
