# Okta Verify for Windows – SCCM Application

## Content folder
```
OktaVerify\
  OktaVerifySetup-<version>.exe   (download from Okta Admin > Settings > Downloads)
  Install-OktaVerify.ps1
  Uninstall-OktaVerify.ps1
  Detect-OktaVerify.ps1
```

## SCCM Deployment Type (Script Installer)
| Setting | Value |
|---|---|
| Install program | `powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\Install-OktaVerify.ps1` |
| Uninstall program | `powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\Uninstall-OktaVerify.ps1` |
| Detection method | Custom script, PowerShell, paste `Detect-OktaVerify.ps1` (set `$MinimumVersion` to the packaged version) |
| Installation behavior | Install for system |
| Logon requirement | Whether or not a user is logged on |
| Visibility | Hidden |
| Return codes | Defaults (0 success, 3010 soft reboot, 1641 hard reboot) |

## Optional installer properties
Pass extra properties with `-InstallArgs`, e.g. for Okta Device Access:
```
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\Install-OktaVerify.ps1 -InstallArgs "SKU=ALL ORGURL=https://yourorg.okta.com CLIENTID=<client id>"
```

## Logs
`C:\ProgramData\OIT\SCCMLogs\OktaVerify_*.log`
