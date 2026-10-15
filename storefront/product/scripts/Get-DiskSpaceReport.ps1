<#
.SYNOPSIS  HTML disk-space report for a list of computers; flags volumes under a free-space threshold.
.EXAMPLE   .\Get-DiskSpaceReport.ps1 -ComputerName srv1,srv2 -ThresholdPercent 15
#>
param(
    [string[]]$ComputerName = $env:COMPUTERNAME,
    [int]$ThresholdPercent = 20,
    [string]$OutFile = "DiskReport_$(Get-Date -Format yyyyMMdd).html"
)
$rows = foreach ($c in $ComputerName) {
    try {
        Get-CimInstance Win32_LogicalDisk -ComputerName $c -Filter 'DriveType=3' -ErrorAction Stop |
        ForEach-Object {
            $pct = [math]::Round(100 * $_.FreeSpace / $_.Size, 1)
            [pscustomobject]@{
                Computer = $c; Drive = $_.DeviceID
                SizeGB = [math]::Round($_.Size / 1GB, 1)
                FreeGB = [math]::Round($_.FreeSpace / 1GB, 1)
                FreePct = $pct
                Status = if ($pct -lt $ThresholdPercent) { 'LOW' } else { 'OK' }
            }
        }
    } catch {
        [pscustomobject]@{ Computer = $c; Drive = '-'; SizeGB = $null; FreeGB = $null; FreePct = $null; Status = "ERROR: $($_.Exception.Message)" }
    }
}
$rows | ConvertTo-Html -Title 'Disk Space Report' |
    ForEach-Object { $_ -replace '<tr><td>(.*)LOW</td>', '<tr style="background:#fdd"><td>$1LOW</td>' } |
    Set-Content $OutFile
Write-Host "Report written to $OutFile"
