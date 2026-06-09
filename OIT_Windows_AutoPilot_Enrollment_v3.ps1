<#
.NAME
    Windows AutoPilot Enrollment v3
.DESCRIPTION
    Modern UI with:
      - Dark terminal-style activity log
      - Color-coded animated status badge
      - Marquee progress bar during enrollment
      - All-caps section labels with dividers
      - Hover effects on buttons
      - RichTextBox with per-line color coding
      - Final confirmation dialog
      - Strict selection gating
      - Group Tag search/filter
      - File logging
      - Copy summary button

    Backend flow (unchanged):
      Install-PackageProvider -Name NuGet -Force
      Install-Script Get-WindowsAutopilotInfo -Force
      Get-WindowsAutoPilotInfo.ps1 -GroupTag <tag> -Online -AddToGroup <group> -Assign
#>
param([switch]$Elevated)

function Test-Admin {
    $currentUser = New-Object Security.Principal.WindowsPrincipal $([Security.Principal.WindowsIdentity]::GetCurrent())
    $currentUser.IsInRole([Security.Principal.WindowsBuiltinRole]::Administrator)
}

if ((Test-Admin) -eq $false) {
    if (-not $Elevated) {
        $exePath = [System.Diagnostics.Process]::GetCurrentProcess().MainModule.FileName
        Start-Process -FilePath $exePath -Verb RunAs -ArgumentList '-elevated'
    }
    Exit
}

Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing
[System.Windows.Forms.Application]::EnableVisualStyles()

$script:AllGroupTags = @(
    'A&S'
    'ADAP'
    'ADV'
    'AIME'
    'AFROTC'
    'Alabama Heritage'
    'Alabama Transportation Institute'
    'Alabama Water Institute'
    'AROTC'
    'Capstone Center for Student Success'
    'Capstone International Center'
    'CBER'
    'CHES'
    'Community Affairs'
    'Diversity, Equity and Inclusion'
    'EM'
    'F&O'
    'Grad School'
    'Honors College'
    'Institutional Effectiveness'
    'Institutional Research and Assessment'
    'Office of Research and Development'
    'ODS'
    'Office for Research Compliance'
    'OIT'
    'Social Work'
    'Strategic Communications'
    'Student Life'
    'Transportation Policy Research Center'
    'UA'
    'UA Press'
    'UAPD'
    'UASYS'
)
$script:LastSummary = ''
$script:LogFolder = Join-Path ($env:ProgramData -replace '^$', $env:SystemDrive + '\ProgramData') 'OIT\AutopilotLogs'
$null = New-Item -Path $script:LogFolder -ItemType Directory -Force -ErrorAction SilentlyContinue
$script:LogPath = Join-Path $script:LogFolder ('Autopilot_' + (Get-Date -Format 'yyyyMMdd_HHmmss') + '.log')

# ── Palette ───────────────────────────────────────────────────────────────────
$bgColor      = [System.Drawing.Color]::FromArgb(241,245,249)  # slate-100
$cardColor    = [System.Drawing.Color]::White
$primaryColor = [System.Drawing.Color]::FromArgb(37,99,235)    # blue-600
$primaryHover = [System.Drawing.Color]::FromArgb(29,78,216)    # blue-700
$successColor = [System.Drawing.Color]::FromArgb(16,185,129)   # emerald-500
$errorColor   = [System.Drawing.Color]::FromArgb(239,68,68)    # red-500
$textColor    = [System.Drawing.Color]::FromArgb(15,23,42)     # slate-900
$mutedColor   = [System.Drawing.Color]::FromArgb(100,116,139)  # slate-500
$borderColor  = [System.Drawing.Color]::FromArgb(226,232,240)  # slate-200
$logBg        = [System.Drawing.Color]::FromArgb(15,23,42)     # dark terminal

# Larger fonts for improved readability
$fontTitle  = New-Object System.Drawing.Font('Segoe UI Semibold', 17)
$fontSub    = New-Object System.Drawing.Font('Segoe UI', 10)
$fontCaps   = New-Object System.Drawing.Font('Segoe UI Semibold', 9)
$fontUI     = New-Object System.Drawing.Font('Segoe UI', 10.5)
$fontBtn    = New-Object System.Drawing.Font('Segoe UI Semibold', 10.5)
$fontBadge  = New-Object System.Drawing.Font('Segoe UI Semibold', 9.5)
$fontMono   = New-Object System.Drawing.Font('Consolas', 10)

# ── Form ──────────────────────────────────────────────────────────────────────
$form = New-Object System.Windows.Forms.Form
$form.ClientSize      = New-Object System.Drawing.Size(740, 940)
$form.StartPosition   = 'CenterScreen'
$form.Text            = 'Windows AutoPilot Enrollment'
$form.TopMost         = $true
$form.BackColor       = $bgColor
$form.FormBorderStyle = 'FixedDialog'
$form.MaximizeBox     = $false
$form.Font            = $fontUI

if ($PSScriptRoot) {
    $iconPath = Join-Path $PSScriptRoot 'A-Square-Logo-4c_Official.ico'
    if (Test-Path $iconPath) { $form.Icon = [System.Drawing.Icon]::ExtractAssociatedIcon($iconPath) }
}

# ── Header image ──────────────────────────────────────────────────────────────
$UAimage = New-Object System.Windows.Forms.PictureBox
$UAimage.Width    = 740
$UAimage.Height   = 155
$UAimage.Location = New-Object System.Drawing.Point(0, 0)
if ($PSScriptRoot) {
    $imgPath = Join-Path $PSScriptRoot 'OIT - MAIN.png'
    if (Test-Path $imgPath) { $UAimage.ImageLocation = $imgPath }
}
$UAimage.SizeMode   = [System.Windows.Forms.PictureBoxSizeMode]::Zoom
$UAimage.BackColor  = $bgColor

# ── Drop-shadow (offset dark panel behind card) ───────────────────────────────
$shadow = New-Object System.Windows.Forms.Panel
$shadow.Location  = New-Object System.Drawing.Point(18, 171)
$shadow.Size      = New-Object System.Drawing.Size(708, 760)
$shadow.BackColor = [System.Drawing.Color]::FromArgb(203, 213, 225)  # slate-300

# ── Main card panel ───────────────────────────────────────────────────────────
$panel = New-Object System.Windows.Forms.Panel
$panel.Location    = New-Object System.Drawing.Point(16, 169)
$panel.Size        = New-Object System.Drawing.Size(708, 760)
$panel.BackColor   = $cardColor
$panel.BorderStyle = 'None'

# ── Title row ─────────────────────────────────────────────────────────────────
$Header = New-Object System.Windows.Forms.Label
$Header.Text      = 'OIT Desktop - AutoPilot Enrollment'
$Header.Location  = New-Object System.Drawing.Point(24, 18)
$Header.Size      = New-Object System.Drawing.Size(560, 34)
$Header.ForeColor = $textColor
$Header.Font      = $fontTitle

$SubHeader = New-Object System.Windows.Forms.Label
$SubHeader.Text      = 'Registers this device in Windows Autopilot using the OIT enrollment backend.'
$SubHeader.Location  = New-Object System.Drawing.Point(26, 56)
$SubHeader.Size      = New-Object System.Drawing.Size(580, 20)
$SubHeader.ForeColor = $mutedColor
$SubHeader.Font      = $fontSub

# Status badge (pill panel)
$statusBadge = New-Object System.Windows.Forms.Panel
$statusBadge.Location  = New-Object System.Drawing.Point(594, 18)
$statusBadge.Size      = New-Object System.Drawing.Size(100, 32)
$statusBadge.BackColor = [System.Drawing.Color]::FromArgb(209, 250, 229)  # emerald-100

$StatusValue = New-Object System.Windows.Forms.Label
$StatusValue.Text      = '* Ready'
$StatusValue.Location  = New-Object System.Drawing.Point(0, 0)
$StatusValue.Size      = New-Object System.Drawing.Size(100, 32)
$StatusValue.TextAlign = [System.Drawing.ContentAlignment]::MiddleCenter
$StatusValue.ForeColor = $successColor
$StatusValue.Font      = $fontBadge
$statusBadge.Controls.Add($StatusValue)

# Divider 1
$div1 = New-Object System.Windows.Forms.Panel
$div1.Location  = New-Object System.Drawing.Point(0, 88)
$div1.Size      = New-Object System.Drawing.Size(708, 1)
$div1.BackColor = $borderColor

# ── Use Case section ──────────────────────────────────────────────────────────
$Label2 = New-Object System.Windows.Forms.Label
$Label2.Text      = 'USE CASE'
$Label2.Location  = New-Object System.Drawing.Point(28, 100)
$Label2.Size      = New-Object System.Drawing.Size(200, 18)
$Label2.ForeColor = $mutedColor
$Label2.Font      = $fontCaps

$UseCaseHelp = New-Object System.Windows.Forms.Label
$UseCaseHelp.Text      = 'Individual  ->  Azure AD Join          Shared  ->  Shared (Azure Joined)'
$UseCaseHelp.Location  = New-Object System.Drawing.Point(28, 122)
$UseCaseHelp.Size      = New-Object System.Drawing.Size(560, 20)
$UseCaseHelp.ForeColor = $mutedColor
$UseCaseHelp.Font      = $fontSub

$UseCase = New-Object System.Windows.Forms.ComboBox
$UseCase.DropDownStyle = [System.Windows.Forms.ComboBoxStyle]::DropDownList
$UseCase.Width    = 280
$UseCase.Location = New-Object System.Drawing.Point(28, 148)
$UseCase.Font     = $fontUI
@('Individual', 'Shared') | ForEach-Object { [void]$UseCase.Items.Add($_) }

# Divider 2
$div2 = New-Object System.Windows.Forms.Panel
$div2.Location  = New-Object System.Drawing.Point(0, 196)
$div2.Size      = New-Object System.Drawing.Size(708, 1)
$div2.BackColor = $borderColor

# ── Group Tag section ─────────────────────────────────────────────────────────
$Label1 = New-Object System.Windows.Forms.Label
$Label1.Text      = 'GROUP TAG'
$Label1.Location  = New-Object System.Drawing.Point(28, 208)
$Label1.Size      = New-Object System.Drawing.Size(200, 18)
$Label1.ForeColor = $mutedColor
$Label1.Font      = $fontCaps

$Label4 = New-Object System.Windows.Forms.Label
$Label4.Text      = 'Select the department or organization to register this device under.'
$Label4.Location  = New-Object System.Drawing.Point(28, 230)
$Label4.Size      = New-Object System.Drawing.Size(560, 20)
$Label4.ForeColor = $mutedColor
$Label4.Font      = $fontSub

$SearchLabel = New-Object System.Windows.Forms.Label
$SearchLabel.Text      = 'Filter:'
$SearchLabel.Location  = New-Object System.Drawing.Point(28, 256)
$SearchLabel.Size      = New-Object System.Drawing.Size(44, 28)
$SearchLabel.ForeColor = $mutedColor
$SearchLabel.Font      = $fontSub
$SearchLabel.TextAlign = [System.Drawing.ContentAlignment]::MiddleLeft

$SearchBox = New-Object System.Windows.Forms.TextBox
$SearchBox.Location = New-Object System.Drawing.Point(76, 256)
$SearchBox.Size     = New-Object System.Drawing.Size(200, 28)
$SearchBox.Font     = $fontUI
$SearchBox.Enabled  = $false

$Area = New-Object System.Windows.Forms.ComboBox
$Area.DropDownStyle = [System.Windows.Forms.ComboBoxStyle]::DropDownList
$Area.Width    = 374
$Area.Location = New-Object System.Drawing.Point(292, 256)
$Area.Font     = $fontUI
$Area.Enabled  = $false

# Divider 3
$div3 = New-Object System.Windows.Forms.Panel
$div3.Location  = New-Object System.Drawing.Point(0, 304)
$div3.Size      = New-Object System.Drawing.Size(708, 1)
$div3.BackColor = $borderColor

# ── Selection summary card ─────────────────────────────────────────────────────
$SummaryPanel = New-Object System.Windows.Forms.Panel
$SummaryPanel.Location  = New-Object System.Drawing.Point(28, 318)
$SummaryPanel.Size      = New-Object System.Drawing.Size(652, 72)
$SummaryPanel.BackColor = [System.Drawing.Color]::FromArgb(248, 250, 252)
$SummaryPanel.BorderStyle = 'FixedSingle'

$SummaryTitle = New-Object System.Windows.Forms.Label
$SummaryTitle.Text      = 'SELECTION SUMMARY'
$SummaryTitle.Location  = New-Object System.Drawing.Point(14, 10)
$SummaryTitle.Size      = New-Object System.Drawing.Size(220, 16)
$SummaryTitle.ForeColor = $mutedColor
$SummaryTitle.Font      = $fontCaps

$SummaryValue = New-Object System.Windows.Forms.Label
$SummaryValue.Text      = 'Select Use Case and Group Tag to see a preview.'
$SummaryValue.Location  = New-Object System.Drawing.Point(14, 32)
$SummaryValue.Size      = New-Object System.Drawing.Size(620, 26)
$SummaryValue.ForeColor = $mutedColor
$SummaryValue.Font      = $fontUI
$SummaryPanel.Controls.AddRange(@($SummaryTitle, $SummaryValue))

# Divider 4
$div4 = New-Object System.Windows.Forms.Panel
$div4.Location  = New-Object System.Drawing.Point(0, 406)
$div4.Size      = New-Object System.Drawing.Size(708, 1)
$div4.BackColor = $borderColor

# ── Verification + Action buttons ─────────────────────────────────────────────
$CheckBox1 = New-Object System.Windows.Forms.CheckBox
$CheckBox1.Text     = 'I have verified these selections and wish to proceed.'
$CheckBox1.Location = New-Object System.Drawing.Point(28, 420)
$CheckBox1.Size     = New-Object System.Drawing.Size(420, 24)
$CheckBox1.Font     = $fontUI
$CheckBox1.Enabled  = $false

$Confirm = New-Object System.Windows.Forms.Button
$Confirm.Text      = 'Start Enrollment'
$Confirm.Location  = New-Object System.Drawing.Point(28, 458)
$Confirm.Size      = New-Object System.Drawing.Size(170, 42)
$Confirm.Font      = $fontBtn
$Confirm.BackColor = $primaryColor
$Confirm.ForeColor = [System.Drawing.Color]::White
$Confirm.FlatStyle = 'Flat'
$Confirm.FlatAppearance.BorderSize = 0
$Confirm.Enabled   = $false
$Confirm.Cursor    = [System.Windows.Forms.Cursors]::Hand
$Confirm.Add_MouseEnter({ if ($Confirm.Enabled) { $Confirm.BackColor = $primaryHover } })
$Confirm.Add_MouseLeave({ $Confirm.BackColor = $primaryColor })

$CopySummary = New-Object System.Windows.Forms.Button
$CopySummary.Text      = 'Copy Summary'
$CopySummary.Location  = New-Object System.Drawing.Point(212, 458)
$CopySummary.Size      = New-Object System.Drawing.Size(140, 42)
$CopySummary.Font      = $fontBtn
$CopySummary.FlatStyle = 'Flat'
$CopySummary.FlatAppearance.BorderColor = $borderColor
$CopySummary.FlatAppearance.BorderSize  = 1
$CopySummary.BackColor = $cardColor
$CopySummary.ForeColor = $textColor
$CopySummary.Enabled   = $false
$CopySummary.Cursor    = [System.Windows.Forms.Cursors]::Hand
$CopySummary.Add_MouseEnter({ if ($CopySummary.Enabled) { $CopySummary.BackColor = [System.Drawing.Color]::FromArgb(241,245,249) } })
$CopySummary.Add_MouseLeave({ $CopySummary.BackColor = $cardColor })

$cancelbutton = New-Object System.Windows.Forms.Button
$cancelbutton.Text      = 'Close'
$cancelbutton.Location  = New-Object System.Drawing.Point(366, 458)
$cancelbutton.Size      = New-Object System.Drawing.Size(100, 42)
$cancelbutton.Font      = $fontBtn
$cancelbutton.FlatStyle = 'Flat'
$cancelbutton.FlatAppearance.BorderColor = $borderColor
$cancelbutton.FlatAppearance.BorderSize  = 1
$cancelbutton.BackColor = $cardColor
$cancelbutton.ForeColor = $textColor
$cancelbutton.Cursor    = [System.Windows.Forms.Cursors]::Hand
$cancelbutton.Add_MouseEnter({ $cancelbutton.BackColor = [System.Drawing.Color]::FromArgb(241,245,249) })
$cancelbutton.Add_MouseLeave({ $cancelbutton.BackColor = $cardColor })
$cancelbutton.Add_Click({ $form.Close() })
$form.CancelButton = $cancelbutton

# Divider 5
$div5 = New-Object System.Windows.Forms.Panel
$div5.Location  = New-Object System.Drawing.Point(0, 514)
$div5.Size      = New-Object System.Drawing.Size(708, 1)
$div5.BackColor = $borderColor

# ── Progress bar ──────────────────────────────────────────────────────────────
$ProgressBar = New-Object System.Windows.Forms.ProgressBar
$ProgressBar.Location = New-Object System.Drawing.Point(28, 526)
$ProgressBar.Size     = New-Object System.Drawing.Size(652, 6)
$ProgressBar.Style    = 'Marquee'
$ProgressBar.MarqueeAnimationSpeed = 0   # stopped until enrollment starts

# ── Activity log ──────────────────────────────────────────────────────────────
$ActivityLabel = New-Object System.Windows.Forms.Label
$ActivityLabel.Text      = 'ACTIVITY LOG'
$ActivityLabel.Location  = New-Object System.Drawing.Point(28, 542)
$ActivityLabel.Size      = New-Object System.Drawing.Size(200, 18)
$ActivityLabel.ForeColor = $mutedColor
$ActivityLabel.Font      = $fontCaps

$outputbox = New-Object System.Windows.Forms.RichTextBox
$outputbox.Location    = New-Object System.Drawing.Point(28, 566)
$outputbox.Size        = New-Object System.Drawing.Size(652, 178)
$outputbox.ReadOnly    = $true
$outputbox.Font        = $fontMono
$outputbox.BackColor   = $logBg
$outputbox.ForeColor   = [System.Drawing.Color]::FromArgb(148, 163, 184)  # slate-400
$outputbox.BorderStyle = 'None'
$outputbox.ScrollBars  = 'Vertical'

# ── Assemble panel ────────────────────────────────────────────────────────────
$panel.Controls.AddRange(@(
    $Header, $SubHeader, $statusBadge,
    $div1,
    $Label2, $UseCaseHelp, $UseCase,
    $div2,
    $Label1, $Label4, $SearchLabel, $SearchBox, $Area,
    $div3,
    $SummaryPanel,
    $div4,
    $CheckBox1, $Confirm, $CopySummary, $cancelbutton,
    $div5,
    $ProgressBar, $ActivityLabel, $outputbox
))

$form.Controls.AddRange(@($shadow, $UAimage, $panel))

# ── Helpers ───────────────────────────────────────────────────────────────────
function Add-OutputBoxLine {
    param($Message)
    $line = '[{0}] {1}' -f (Get-Date -Format s), $Message
    $outputbox.SelectionStart  = $outputbox.TextLength
    $outputbox.SelectionLength = 0
    if ($Message -like 'ERROR*') {
        $outputbox.SelectionColor = [System.Drawing.Color]::FromArgb(252, 165, 165)   # red-300
    } elseif ($Message -match '(completed|installed|updated|copied|Ready)') {
        $outputbox.SelectionColor = [System.Drawing.Color]::FromArgb(110, 231, 183)   # emerald-300
    } elseif ($Message -like '---*') {
        $outputbox.SelectionColor = [System.Drawing.Color]::FromArgb(51, 65, 85)      # slate-700
    } else {
        $outputbox.SelectionColor = [System.Drawing.Color]::FromArgb(148, 163, 184)   # slate-400
    }
    $outputbox.AppendText("$line`r`n")
    $outputbox.ScrollToCaret()
    $outputbox.Refresh()
    Add-Content -LiteralPath $script:LogPath -Value $line
}

function Set-Status {
    param([string]$Value)
    switch ($Value) {
        'Ready' {
            $StatusValue.Text      = '* Ready'
            $StatusValue.ForeColor = $successColor
            $statusBadge.BackColor = [System.Drawing.Color]::FromArgb(209, 250, 229)
            $ProgressBar.MarqueeAnimationSpeed = 0
        }
        'Installing prerequisites' {
            $StatusValue.Text      = '* Installing'
            $StatusValue.ForeColor = [System.Drawing.Color]::FromArgb(59, 130, 246)
            $statusBadge.BackColor = [System.Drawing.Color]::FromArgb(219, 234, 254)
            $ProgressBar.MarqueeAnimationSpeed = 30
        }
        'Registering device' {
            $StatusValue.Text      = '* Registering'
            $StatusValue.ForeColor = [System.Drawing.Color]::FromArgb(59, 130, 246)
            $statusBadge.BackColor = [System.Drawing.Color]::FromArgb(219, 234, 254)
            $ProgressBar.MarqueeAnimationSpeed = 30
        }
        'Complete' {
            $StatusValue.Text      = '* Complete'
            $StatusValue.ForeColor = $successColor
            $statusBadge.BackColor = [System.Drawing.Color]::FromArgb(209, 250, 229)
            $ProgressBar.MarqueeAnimationSpeed = 0
        }
        'Error' {
            $StatusValue.Text      = '* Error'
            $StatusValue.ForeColor = $errorColor
            $statusBadge.BackColor = [System.Drawing.Color]::FromArgb(254, 226, 226)
            $ProgressBar.MarqueeAnimationSpeed = 0
        }
        default {
            $StatusValue.Text      = "* $Value"
            $StatusValue.ForeColor = [System.Drawing.Color]::FromArgb(59, 130, 246)
            $statusBadge.BackColor = [System.Drawing.Color]::FromArgb(219, 234, 254)
        }
    }
}

function Get-AddToGroupValue {
    switch ($UseCase.Text) {
        'Individual' { return 'Azure' }
        'Shared'     { return 'Shared (Azure Joined)' }
        default      { throw 'Invalid use case selected.' }
    }
}

function Refresh-GroupTags {
    $selected = $Area.Text
    $filter   = $SearchBox.Text.Trim()
    $Area.Items.Clear()
    $filteredTags = @($script:AllGroupTags | Where-Object {
        if ([string]::IsNullOrWhiteSpace($filter)) { $true } else { $_ -like "*$filter*" }
    })
    foreach ($tag in $filteredTags) { [void]$Area.Items.Add($tag) }
    if ($selected -and $filteredTags -contains $selected) { $Area.SelectedItem = $selected }
}

function Update-Summary {
    if ($UseCase.SelectedItem -and $Area.SelectedItem) {
        $addGroup = Get-AddToGroupValue
        $SummaryValue.Text      = "Use Case: $($UseCase.Text)     |     Group Tag: $($Area.Text)     |     AddToGroup: $addGroup"
        $SummaryValue.ForeColor = $textColor
        $CheckBox1.Enabled      = $true
    } else {
        $SummaryValue.Text      = 'Select Use Case and Group Tag to see a preview.'
        $SummaryValue.ForeColor = $mutedColor
        $CheckBox1.Enabled      = $false
        $CheckBox1.Checked      = $false
        $Confirm.Enabled        = $false
    }
}

# ── Event handlers ────────────────────────────────────────────────────────────
$UseCase.Add_SelectedValueChanged({
    $Area.Enabled      = $true
    $SearchBox.Enabled = $true
    $CheckBox1.Checked = $false
    Update-Summary
})

$SearchBox.Add_TextChanged({
    Refresh-GroupTags
    $CheckBox1.Checked = $false
    Update-Summary
})

$Area.Add_SelectedValueChanged({
    $CheckBox1.Checked = $false
    Update-Summary
})

$CheckBox1.Add_CheckedChanged({
    $Confirm.Enabled = [bool]$CheckBox1.Checked
})

$CopySummary.Add_Click({
    $text = if ($script:syncHash) { $script:syncHash.LastSummary } else { $script:LastSummary }
    if (-not [string]::IsNullOrWhiteSpace($text)) {
        [System.Windows.Forms.Clipboard]::SetText($text)
        Add-OutputBoxLine 'Summary copied to clipboard.'
    }
})

$Confirm.Add_Click({
    if (-not $UseCase.SelectedItem -or -not $Area.SelectedItem) {
        [System.Windows.Forms.MessageBox]::Show(
            'Select both Use Case and Group Tag before continuing.',
            'Validation', [System.Windows.Forms.MessageBoxButtons]::OK,
            [System.Windows.Forms.MessageBoxIcon]::Warning
        ) | Out-Null
        return
    }

    $UseCase_Text = $UseCase.Text
    $Area_Text    = $Area.Text
    $AddToGroup   = Get-AddToGroupValue

    $finalPrompt = "Use Case: $UseCase_Text`r`nGroup Tag: $Area_Text`r`nAddToGroup: $AddToGroup`r`n`r`nThe device will reboot when enrollment completes.`r`n`r`nContinue?"
    $result = [System.Windows.Forms.MessageBox]::Show(
        $finalPrompt, 'Confirm Enrollment',
        [System.Windows.Forms.MessageBoxButtons]::YesNo,
        [System.Windows.Forms.MessageBoxIcon]::Question
    )
    if ($result -ne [System.Windows.Forms.DialogResult]::Yes) { return }

    $Confirm.Enabled    = $false
    $CheckBox1.Enabled  = $false
    $Area.Enabled       = $false
    $UseCase.Enabled    = $false
    $SearchBox.Enabled  = $false
    Set-Status 'Installing prerequisites'

    Add-OutputBoxLine 'Started'
    Add-OutputBoxLine "Use Case          = $UseCase_Text"
    Add-OutputBoxLine "Group Tag         = $Area_Text"
    Add-OutputBoxLine "AddToGroup        = $AddToGroup"
    Add-OutputBoxLine 'Assigned User     = Not set by this tool'
    Add-OutputBoxLine "Log File          = $script:LogPath"
    Add-OutputBoxLine '----------------------------------------'

    # Capture variables for the background runspace
    $logPath      = $script:LogPath
    $syncHash     = [hashtable]::Synchronized(@{
        Form        = $form
        Outputbox   = $outputbox
        StatusValue = $StatusValue
        StatusBadge = $statusBadge
        ProgressBar = $ProgressBar
        CopySummary = $CopySummary
        CheckBox1   = $CheckBox1
        Area        = $Area
        UseCase     = $UseCase
        SearchBox   = $SearchBox
        Confirm     = $Confirm
        LogPath     = $logPath
        UseCase_Text= $UseCase_Text
        Area_Text   = $Area_Text
        AddToGroup  = $AddToGroup
        SuccessColor= $successColor
        ErrorColor  = $errorColor
        PrimaryColor= $primaryColor
        LastSummary = ''
        Done        = $false
    })

    $rs = [runspacefactory]::CreateRunspace()
    $rs.ApartmentState = 'STA'
    $rs.ThreadOptions  = 'ReuseThread'
    $rs.Open()
    $rs.SessionStateProxy.SetVariable('syncHash', $syncHash)

    $ps = [powershell]::Create()
    $ps.Runspace = $rs
    [void]$ps.AddScript({
        function Sync-UI {
            param([scriptblock]$Action)
            $syncHash.Form.Invoke([Action]$Action)
        }

        function Write-Log {
            param($Message)
            $line = '[{0}] {1}' -f (Get-Date -Format s), $Message
            Add-Content -LiteralPath $syncHash.LogPath -Value $line
            Sync-UI {
                $syncHash.Outputbox.SelectionStart  = $syncHash.Outputbox.TextLength
                $syncHash.Outputbox.SelectionLength = 0
                if ($Message -like 'ERROR*') {
                    $syncHash.Outputbox.SelectionColor = [System.Drawing.Color]::FromArgb(252,165,165)
                } elseif ($Message -match '(completed|installed|updated|Ready)') {
                    $syncHash.Outputbox.SelectionColor = [System.Drawing.Color]::FromArgb(110,231,183)
                } elseif ($Message -like '---*') {
                    $syncHash.Outputbox.SelectionColor = [System.Drawing.Color]::FromArgb(51,65,85)
                } else {
                    $syncHash.Outputbox.SelectionColor = [System.Drawing.Color]::FromArgb(148,163,184)
                }
                $syncHash.Outputbox.AppendText("$line`r`n")
                $syncHash.Outputbox.ScrollToCaret()
            }
        }

        function Set-UIStatus {
            param([string]$Value)
            Sync-UI {
                switch ($Value) {
                    'Installing prerequisites' {
                        $syncHash.StatusValue.Text      = '* Installing'
                        $syncHash.StatusValue.ForeColor = [System.Drawing.Color]::FromArgb(59,130,246)
                        $syncHash.StatusBadge.BackColor = [System.Drawing.Color]::FromArgb(219,234,254)
                        $syncHash.ProgressBar.MarqueeAnimationSpeed = 30
                    }
                    'Registering device' {
                        $syncHash.StatusValue.Text      = '* Registering'
                        $syncHash.StatusValue.ForeColor = [System.Drawing.Color]::FromArgb(59,130,246)
                        $syncHash.StatusBadge.BackColor = [System.Drawing.Color]::FromArgb(219,234,254)
                        $syncHash.ProgressBar.MarqueeAnimationSpeed = 30
                    }
                    'Complete' {
                        $syncHash.StatusValue.Text      = '* Complete'
                        $syncHash.StatusValue.ForeColor = $syncHash.SuccessColor
                        $syncHash.StatusBadge.BackColor = [System.Drawing.Color]::FromArgb(209,250,229)
                        $syncHash.ProgressBar.MarqueeAnimationSpeed = 0
                    }
                    'Error' {
                        $syncHash.StatusValue.Text      = '* Error'
                        $syncHash.StatusValue.ForeColor = $syncHash.ErrorColor
                        $syncHash.StatusBadge.BackColor = [System.Drawing.Color]::FromArgb(254,226,226)
                        $syncHash.ProgressBar.MarqueeAnimationSpeed = 0
                    }
                }
            }
        }

        try {
            Install-PackageProvider -Name NuGet -Force | Out-Null
            Write-Log 'NuGet provider installed'

            Install-Script Get-WindowsAutopilotInfo -Force | Out-Null
            Write-Log 'Get-WindowsAutopilotInfo installed/updated'

            Set-UIStatus 'Registering device'
            $autopilotScript = (Get-Command 'Get-WindowsAutoPilotInfo.ps1' -ErrorAction Stop).Source
            $enrollment = & $autopilotScript -GroupTag $syncHash.Area_Text -Online -AddToGroup $syncHash.AddToGroup -Assign 2>&1
            if ($enrollment) { foreach ($l in $enrollment) { Write-Log ([string]$l) } }

            Set-UIStatus 'Complete'
            Write-Log 'Enrollment completed'

            $syncHash.LastSummary = @"
Timestamp:     $(Get-Date -Format s)
Use Case:      $($syncHash.UseCase_Text)
Group Tag:     $($syncHash.Area_Text)
AddToGroup:    $($syncHash.AddToGroup)
Assigned User: Not set by this tool
Log File:      $($syncHash.LogPath)
Status:        Complete
"@
            Sync-UI { $syncHash.CopySummary.Enabled = $true }

            Sync-UI {
                [void][System.Windows.Forms.MessageBox]::Show(
                    'Enrollment is finished. Click OK to reboot.',
                    'Reboot', [System.Windows.Forms.MessageBoxButtons]::OK,
                    [System.Windows.Forms.MessageBoxIcon]::None
                )
            }
            Restart-Computer -Force
        }
        catch {
            $errMsg = $_.Exception.Message
            Set-UIStatus 'Error'
            Write-Log "ERROR: $errMsg"
            Sync-UI {
                [System.Windows.Forms.MessageBox]::Show(
                    $errMsg, 'Autopilot Enrollment Error',
                    [System.Windows.Forms.MessageBoxButtons]::OK,
                    [System.Windows.Forms.MessageBoxIcon]::Error
                ) | Out-Null
                $syncHash.CheckBox1.Enabled = $true
                $syncHash.Area.Enabled      = $true
                $syncHash.UseCase.Enabled   = $true
                $syncHash.SearchBox.Enabled = $true
                $syncHash.Confirm.Enabled   = [bool]$syncHash.CheckBox1.Checked
            }
        }
        finally {
            $syncHash.Done = $true
        }
    })

    [void]$ps.BeginInvoke()
})

# ── Init ──────────────────────────────────────────────────────────────────────
try {
    Refresh-GroupTags
    $SearchBox.Enabled = $false

    $form.Add_Shown({
        Add-OutputBoxLine 'Ready.'
        Add-OutputBoxLine "Log file: $script:LogPath"
    })

    [System.Windows.Forms.Application]::Run($form)
} catch {
    [System.Windows.Forms.MessageBox]::Show(
        $_.Exception.Message, 'Startup Error',
        [System.Windows.Forms.MessageBoxButtons]::OK,
        [System.Windows.Forms.MessageBoxIcon]::Error
    ) | Out-Null
}
