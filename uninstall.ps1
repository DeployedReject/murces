# ==============================================================================
#  __  __
# |  \/  |_   _ _ __ ___ ___  ___
# | |\/| | | | | '__/ __/ _ \/ __|
# | |  | | |_| | | | (_|  __/\__ \
# |_|  |_|\__,_|_|  \___\___||___/
#    Minecraft Server Manager - Windows PowerShell Uninstaller
# ==============================================================================

[CmdletBinding()]
param(
    [switch]$Unattended = $false
)

$ErrorActionPreference = "Continue"

function Write-Color([string]$text, [string]$color = "White", [switch]$NoNewline) {
    if ($NoNewline) {
        Write-Host $text -ForegroundColor $color -NoNewline
    } else {
        Write-Host $text -ForegroundColor $color
    }
}

function Show-Banner {
    Clear-Host
    Write-Color @"
  __  __
 |  \/  |_   _ _ __ ___ ___  ___
 | |\/| | | | | '__/ __/ _ \/ __|
 | |  | | |_| | | | (_|  __/\__ \
 |_|  |_|\__,_|_|  \___\___||___/
"@ "Cyan"
    Write-Color "  Minecraft Server Manager — Windows Uninstaller" "DarkGray"
    Write-Color "------------------------------------------------------------" "Cyan"
    Write-Host ""
}

function Ask-Consent([string]$prompt, [bool]$defaultYes = $true) {
    if ($Unattended) { return $true }
    $suffix = if ($defaultYes) { "[Y/n]" } else { "[y/N]" }
    Write-Color "  ? " "Yellow" -NoNewline
    Write-Color "$prompt ${suffix}: " "White" -NoNewline
    $response = Read-Host
    if ([string]::IsNullOrWhiteSpace($response)) {
        return $defaultYes
    }
    return ($response.Trim().ToLower() -in @("y", "yes"))
}

Show-Banner

# 1. Target Directories
$possibleDirs = @(
    (Join-Path $env:LOCALAPPDATA "MurCes"),
    (Join-Path $HOME ".murces")
)

$foundDirs = @()
foreach ($d in $possibleDirs) {
    if (Test-Path $d) {
        $foundDirs += $d
    }
}

if ($foundDirs.Count -eq 0) {
    Write-Color "  ⚠ No standard MurCes installation directory found." "Yellow"
} else {
    Write-Color "Found MurCes installation(s):" "White"
    foreach ($d in $foundDirs) {
        Write-Color "  - $d" "Cyan"
    }
    Write-Host ""

    if (Ask-Consent "Remove MurCes executable and installation folder(s)?" $true) {
        foreach ($d in $foundDirs) {
            try {
                Remove-Item -Path $d -Recurse -Force -ErrorAction Stop
                Write-Color "  ✔ Removed: $d" "Green"
            } catch {
                Write-Color "  ✖ Error removing $d: $_" "Red"
            }
        }
    }
}
Write-Host ""

# 2. User Environment PATH cleanup
if (Ask-Consent "Remove MurCes from user PATH environment variable?" $true) {
    try {
        $currentPath = [Environment]::GetEnvironmentVariable("Path", [EnvironmentVariableTarget]::User)
        if ($currentPath) {
            $pathParts = $currentPath -split ";"
            $filtered = @()
            $removedAny = $false
            foreach ($p in $pathParts) {
                if ($p -match "MurCes" -or $p -match "\\\.murces") {
                    $removedAny = $true
                } elseif (-not [string]::IsNullOrWhiteSpace($p)) {
                    $filtered += $p
                }
            }
            if ($removedAny) {
                $newPath = $filtered -join ";"
                [Environment]::SetEnvironmentVariable("Path", $newPath, [EnvironmentVariableTarget]::User)
                Write-Color "  ✔ Cleaned MurCes from user PATH." "Green"
            } else {
                Write-Color "  - No MurCes entry found in user PATH." "DarkGray"
            }
        }
    } catch {
        Write-Color "  ✖ Failed to update PATH: $_" "Red"
    }
}

Write-Host ""
Write-Color "✦ Uninstallation complete!" "Green"
Write-Host ""
