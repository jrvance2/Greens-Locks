<#
.SYNOPSIS  Remove local user profiles unused for N days (shared PCs, labs, kiosks).
.EXAMPLE   .\Remove-OldProfiles.ps1 -Days 60 -WhatIf
#>
[CmdletBinding(SupportsShouldProcess, ConfirmImpact='High')]
param([int]$Days = 60, [string[]]$Exclude = @('Administrator','Default','Public'))
$cutoff = (Get-Date).AddDays(-$Days)
Get-CimInstance Win32_UserProfile |
    Where-Object { -not $_.Special -and $_.LastUseTime -lt $cutoff -and
                   ($Exclude -notcontains (Split-Path $_.LocalPath -Leaf)) } |
    ForEach-Object {
        if ($PSCmdlet.ShouldProcess($_.LocalPath, 'Remove profile')) { Remove-CimInstance $_ }
    }
