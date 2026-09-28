<#
.NAME
    Detect-OktaVerify
.DESCRIPTION
    SCCM/MECM detection method script (Detection Method > Use a custom script > PowerShell).
    Writes output (= detected) only when Okta Verify is installed at or above $MinimumVersion.
    Set $MinimumVersion to the version of the installer in the package.
#>

$MinimumVersion = [version]'0.0.0'

$keys = @(
    'HKLM:\SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\*'
    'HKLM:\SOFTWARE\WOW6432Node\Microsoft\Windows\CurrentVersion\Uninstall\*'
)

$app = Get-ItemProperty -Path $keys -ErrorAction SilentlyContinue |
    Where-Object { $_.DisplayName -like 'Okta Verify*' -and $_.DisplayVersion } |
    Sort-Object { [version]($_.DisplayVersion -replace '[^\d\.]', '') } -Descending |
    Select-Object -First 1

if ($app) {
    $installed = [version]($app.DisplayVersion -replace '[^\d\.]', '')
    if ($installed -ge $MinimumVersion) {
        Write-Output "Okta Verify $installed detected"
    }
}
exit 0
