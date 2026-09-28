<#
.NAME
    Uninstall-OktaVerify
.DESCRIPTION
    Silent SCCM/MECM uninstall wrapper for Okta Verify for Windows.
    Uses the UninstallString registered by the installer.

    SCCM uninstall command:
      powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\Uninstall-OktaVerify.ps1
#>

$LogFolder = Join-Path $env:ProgramData 'OIT\SCCMLogs'
$null = New-Item -Path $LogFolder -ItemType Directory -Force -ErrorAction SilentlyContinue
$LogPath = Join-Path $LogFolder 'OktaVerify_Uninstall.log'

function Write-Log([string]$Message) {
    $line = '{0}  {1}' -f (Get-Date -Format 'yyyy-MM-dd HH:mm:ss'), $Message
    Add-Content -Path $LogPath -Value $line
    Write-Output $line
}

try {
    Write-Log '===== Okta Verify uninstall started ====='

    $keys = @(
        'HKLM:\SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\*'
        'HKLM:\SOFTWARE\WOW6432Node\Microsoft\Windows\CurrentVersion\Uninstall\*'
    )
    $apps = Get-ItemProperty -Path $keys -ErrorAction SilentlyContinue |
        Where-Object { $_.DisplayName -like 'Okta Verify*' -and $_.UninstallString }

    if (-not $apps) {
        Write-Log 'Okta Verify is not installed. Nothing to do.'
        exit 0
    }

    Get-Process -Name 'OktaVerify' -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue

    $finalCode = 0
    foreach ($app in $apps) {
        $uninst = $app.UninstallString
        Write-Log "Found $($app.DisplayName) $($app.DisplayVersion): $uninst"

        if ($uninst -match 'msiexec') {
            $guid = [regex]::Match($uninst, '\{[0-9A-Fa-f\-]{36}\}').Value
            $file = 'msiexec.exe'
            $argList = "/x $guid /qn /norestart"
        } else {
            # Bundle (exe) uninstaller: split path from any existing args
            if ($uninst -match '^"([^"]+)"\s*(.*)$') { $file = $Matches[1]; $rest = $Matches[2] }
            else { $file = $uninst; $rest = '' }
            $argList = ("$rest /uninstall /q /norestart").Trim()
        }

        Write-Log "Running: `"$file`" $argList"
        $proc = Start-Process -FilePath $file -ArgumentList $argList -Wait -PassThru -WindowStyle Hidden
        Write-Log "Exit code: $($proc.ExitCode)"
        if ($proc.ExitCode -notin 0, 3010, 1641, 1605) { $finalCode = $proc.ExitCode }
        elseif ($proc.ExitCode -in 3010, 1641 -and $finalCode -eq 0) { $finalCode = 3010 }
    }

    exit $finalCode
}
catch {
    Write-Log "ERROR: $($_.Exception.Message)"
    exit 1603
}
