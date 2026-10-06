<#
.SYNOPSIS
    Silently uninstalls Duo Authentication for Windows Logon (RDP/Logon) via its MSI product code.

.DESCRIPTION
    Finds the installed Duo product in the 64-bit and 32-bit Uninstall registry keys, then runs
    msiexec /x against its product code. Looking the code up at run time means the script keeps
    working across Duo versions, since each release has a different product code.

    Usage:
        - SCCM console > Software Library > Scripts > Create Script, paste this file, approve it,
          then right-click a device or collection > Run Script. No parameters are needed.
        - Or locally from an elevated prompt:
              powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\Uninstall-Duo.ps1

    Exit codes:
        0     Success, or Duo is not installed
        3010  Success, reboot required
        1641  Success, reboot initiated
        other msiexec error code (log is written to the path below)
#>

[CmdletBinding()]
param(
    # Matches the DisplayName shown in Programs and Features.
    [string]$DisplayNamePattern = 'Duo Authentication for Windows Logon*',
    [string]$LogDir = "$env:windir\Temp"
)

$ErrorActionPreference = 'Stop'
$log = Join-Path $LogDir 'Duo_Uninstall.log'

function Write-Log {
    param([string]$Message)
    $line = "{0}  {1}" -f (Get-Date -Format 'yyyy-MM-dd HH:mm:ss'), $Message
    $line | Add-Content -Path $log
    Write-Output $line   # shows in the SCCM Run Scripts results
}

$keys = @(
    'HKLM:\SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\*',
    'HKLM:\SOFTWARE\WOW6432Node\Microsoft\Windows\CurrentVersion\Uninstall\*'
)

try {
    $apps = Get-ItemProperty -Path $keys -ErrorAction SilentlyContinue |
        Where-Object { $_.DisplayName -like $DisplayNamePattern }

    if (-not $apps) {
        Write-Log "No product matching '$DisplayNamePattern' found. Nothing to do."
        exit 0
    }

    $exitCode = 0
    foreach ($app in $apps) {
        # Product code is the registry key name, e.g. {XXXXXXXX-XXXX-...}
        $code = $app.PSChildName
        if ($code -notmatch '^\{[0-9A-Fa-f-]{36}\}$') {
            Write-Log "Skipping '$($app.DisplayName)': key '$code' is not an MSI product code."
            continue
        }

        Write-Log "Uninstalling '$($app.DisplayName)' $($app.DisplayVersion) $code"
        $msiLog = Join-Path $LogDir "Duo_Uninstall_msi_$($code.Trim('{}')).log"
        $p = Start-Process -FilePath "$env:windir\System32\msiexec.exe" `
            -ArgumentList "/x $code /qn /norestart /l*v `"$msiLog`"" `
            -Wait -PassThru
        Write-Log "msiexec exit code: $($p.ExitCode)"

        if ($p.ExitCode -notin 0, 1605, 1641, 3010) { $exitCode = $p.ExitCode }   # 1605 = already removed
        elseif ($p.ExitCode -in 1641, 3010 -and $exitCode -eq 0) { $exitCode = $p.ExitCode }
    }
    exit $exitCode
}
catch {
    Write-Log "ERROR: $($_.Exception.Message)"
    exit 1
}
