<#
.SYNOPSIS  Report (and optionally disable) Active Directory accounts inactive for N days.
.EXAMPLE   .\Get-StaleADAccounts.ps1 -Days 90 -OutFile stale.csv
.EXAMPLE   .\Get-StaleADAccounts.ps1 -Days 120 -Disable -WhatIf
#>
[CmdletBinding(SupportsShouldProcess)]
param(
    [int]$Days = 90,
    [string]$OutFile = "StaleAccounts_$(Get-Date -Format yyyyMMdd).csv",
    [switch]$Disable
)
Import-Module ActiveDirectory -ErrorAction Stop
$cutoff = (Get-Date).AddDays(-$Days)
$stale = Get-ADUser -Filter { Enabled -eq $true -and LastLogonTimestamp -lt $cutoff } `
    -Properties LastLogonTimestamp, Description |
    Select-Object SamAccountName, Name, Description,
        @{n='LastLogon';e={[DateTime]::FromFileTime($_.LastLogonTimestamp)}}
$stale | Export-Csv $OutFile -NoTypeInformation
Write-Host "$($stale.Count) stale accounts written to $OutFile"
if ($Disable) {
    foreach ($u in $stale) {
        if ($PSCmdlet.ShouldProcess($u.SamAccountName, 'Disable-ADAccount')) {
            Disable-ADAccount -Identity $u.SamAccountName
        }
    }
}
