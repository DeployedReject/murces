# ==============================================================================
#  __  __
# |  \/  |_   _ _ __ ___ ___  ___
# | |\/| | | | | '__/ __/ _ \/ __|
# | |  | | |_| | | | (_|  __/\__ \
# |_|  |_|\__,_|_|  \___\___||___/
#    Minecraft Server Manager - Windows PowerShell Installer
#
# Consent-driven automated installer for MurCes on Windows:
# - Detects architecture & Windows package managers (winget, scoop, choco)
# - Installs psmux (Windows terminal multiplexer alternative to tmux)
# - Verifies tar, curl, rclone, playit tunnel agent
# - Downloads or deploys MurCes standalone executable
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
    Write-Color "  Minecraft Server Manager — Windows Setup" "DarkGray"
    Write-Color "------------------------------------------------------------" "Cyan"
    Write-Host ""
}

function Ask-Consent([string]$prompt, [bool]$defaultYes = $true) {
    if ($Unattended) { return $true }
    $suffix = if ($defaultYes) { "[Y/n]" } else { "[y/N]" }
    Write-Color "  ? " "Yellow" -NoNewline
    Write-Color "$prompt $suffix: " "White" -NoNewline
    $response = Read-Host
    if ([string]::IsNullOrWhiteSpace($response)) {
        return $defaultYes
    }
    return ($response.Trim().ToLower() -in @("y", "yes"))
}

function Download-FileWithProgress([string]$url, [string]$dest, [string]$label) {
    Write-Color "  ⬇ $label" "Cyan"
    $tempFile = "$dest.tmp"
    if (Test-Path $tempFile) { Remove-Item $tempFile -Force }
    try {
        $ProgressPreference = 'Continue'
        Invoke-WebRequest -Uri $url -OutFile $tempFile -UseBasicParsing
        if ((Test-Path $tempFile) -and ((Get-Item $tempFile).Length -gt 0)) {
            Move-Item $tempFile $dest -Force
            Write-Color "    ✔ Download complete: $dest" "Green"
            return $true
        } else {
            Write-Color "    ✖ Download returned empty file." "Red"
            if (Test-Path $tempFile) { Remove-Item $tempFile -Force }
            return $false
        }
    } catch {
        Write-Color "    ✖ Download failed: $_" "Red"
        if (Test-Path $tempFile) { Remove-Item $tempFile -Force }
        return $false
    }
}

Show-Banner

# 1. System & Architecture Detection
$rawArch = [System.Runtime.InteropServices.RuntimeInformation]::OSArchitecture.ToString().ToLower()
$is64Bit = [Environment]::Is64BitOperatingSystem
$archLabel = "Windows $rawArch"
Write-Color "✦ Architecture detected: $archLabel" "Magenta"

# Detect Windows Package Managers
$hasWinget = [bool](Get-Command "winget" -ErrorAction SilentlyContinue)
$hasScoop = [bool](Get-Command "scoop" -ErrorAction SilentlyContinue)
$hasChoco = [bool](Get-Command "choco" -ErrorAction SilentlyContinue)

$detectedPm = if ($hasWinget) { "winget" } elseif ($hasScoop) { "scoop" } elseif ($hasChoco) { "choco" } else { "none" }
Write-Color "✦ Package manager detected: $detectedPm" "Magenta"
Write-Host ""

# 2. Multiplexer Installation (psmux for Windows)
Write-Color "[1/4] Terminal Multiplexer (psmux)" "White"
$hasPsmux = [bool](Get-Command "psmux" -ErrorAction SilentlyContinue)

if ($hasPsmux) {
    Write-Color "  ✔ psmux is already installed and available in PATH." "Green"
} else {
    Write-Color "  ⚠ psmux is the native Windows terminal multiplexer required by MurCes to manage background Minecraft server sessions." "Yellow"
    if (Ask-Consent "Install psmux (Windows terminal multiplexer) [Required]?" $true) {
        $installed = $false
        if ($hasWinget) {
            Write-Color "  ➔ Installing psmux via winget..." "Cyan"
            winget install psmux --accept-source-agreements --accept-package-agreements
            if ($LASTEXITCODE -eq 0) { $installed = $true }
        } elseif ($hasScoop) {
            Write-Color "  ➔ Installing psmux via scoop..." "Cyan"
            scoop install psmux
            if ($LASTEXITCODE -eq 0) { $installed = $true }
        } elseif ($hasChoco) {
            Write-Color "  ➔ Installing psmux via chocolatey..." "Cyan"
            choco install psmux -y
            if ($LASTEXITCODE -eq 0) { $installed = $true }
        }

        if (-not $installed) {
            Write-Color "  ➔ Downloading precompiled psmux binary..." "Cyan"
            $psmuxUrl = "https://github.com/psmux/psmux/releases/latest/download/psmux-windows-x86_64.zip"
            if (Download-FileWithProgress $psmuxUrl "psmux.zip" "psmux release zip") {
                Expand-Archive -Path "psmux.zip" -DestinationPath "." -Force
                Remove-Item "psmux.zip" -Force
                $installed = $true
            }
        }

        if ($installed) {
            Write-Color "  ✔ psmux setup finished." "Green"
        } else {
            Write-Color "  ✖ Failed to install psmux automatically. You can install it manually with: winget install psmux" "Red"
        }
    } else {
        Write-Color "  - Skipped psmux installation." "DarkGray"
    }
}
Write-Host ""

# 3. System Utilities Check (curl, tar, rclone)
Write-Color "[2/4] Core Dependencies" "White"
$hasCurl = [bool](Get-Command "curl.exe" -ErrorAction SilentlyContinue)
if ($hasCurl) {
    Write-Color "  ✔ curl (HTTP Downloader) is ready." "Green"
} else {
    Write-Color "  ⚠ curl is not found in PATH." "Yellow"
}

$hasTar = [bool](Get-Command "tar.exe" -ErrorAction SilentlyContinue)
if ($hasTar) {
    Write-Color "  ✔ tar (World Archive Bundler) is ready." "Green"
} else {
    Write-Color "  ⚠ tar.exe is not found in PATH." "Yellow"
}

$hasRclone = [bool](Get-Command "rclone" -ErrorAction SilentlyContinue)
if ($hasRclone) {
    Write-Color "  ✔ rclone (Cloud Sync & Google Drive) is ready." "Green"
} else {
    if (Ask-Consent "Install rclone for optional Google Drive cloud backups [Optional]?" $false) {
        if ($hasWinget) {
            winget install Rclone.Rclone --accept-source-agreements --accept-package-agreements
        } elseif ($hasScoop) {
            scoop install rclone
        } elseif ($hasChoco) {
            choco install rclone -y
        } else {
            Download-FileWithProgress "https://downloads.rclone.org/rclone-current-windows-amd64.zip" "rclone.zip" "rclone package"
            Expand-Archive -Path "rclone.zip" -DestinationPath "rclone-dist" -Force
            Get-ChildItem -Path "rclone-dist" -Recurse -Filter "rclone.exe" | Copy-Item -Destination ".\rclone.exe"
            Remove-Item "rclone.zip", "rclone-dist" -Recurse -Force
        }
    } else {
        Write-Color "  - Skipped rclone installation." "DarkGray"
    }
}
Write-Host ""

# 4. Public Tunnels (playit.gg)
Write-Color "[3/4] Public Tunnels (playit.gg)" "White"
$hasPlayit = [bool](Get-Command "playit" -ErrorAction SilentlyContinue) -or (Test-Path ".\playit.exe")

if ($hasPlayit) {
    Write-Color "  ✔ playit tunnel agent is already available." "Green"
} else {
    if (Ask-Consent "Install playit client binary for zero-config public tunnels?" $true) {
        $playitUrl = "https://github.com/playit-cloud/playit-agent/releases/latest/download/playit-windows-x86_64.exe"
        Download-FileWithProgress $playitUrl "playit.exe" "playit tunnel agent (Windows x64)" | Out-Null
    } else {
        Write-Color "  - Skipped playit setup." "DarkGray"
    }
}
Write-Host ""

# 5. MurCes Standalone Executable
Write-Color "[4/4] MurCes Standalone Executable" "White"
$murcesExe = ".\murces.exe"

if (Test-Path $murcesExe) {
    Write-Color "  ✔ murces.exe executable is already present in this directory." "Green"
    if (Ask-Consent "Re-download and overwrite with the latest release from GitHub?" $false) {
        Download-FileWithProgress "https://github.com/DeployedReject/murces/releases/latest/download/murces-windows-amd64.exe" $murcesExe "MurCes Windows Native Executable" | Out-Null
    }
} elseif (Test-Path "java\tui\target\murces.exe") {
    if (Ask-Consent "Deploy locally built native binary 'java\tui\target\murces.exe' to .\murces.exe?" $true) {
        Copy-Item "java\tui\target\murces.exe" $murcesExe -Force
        Write-Color "  ✔ Local murces.exe deployed successfully." "Green"
    }
} elseif (Test-Path "java\tui\target\murces-tui-1.6.jar") {
    if (Ask-Consent "Create murces.bat launcher for locally built JAR 'java\tui\target\murces-tui-1.6.jar'?" $true) {
        "@echo off`njava -jar `"%~dp0java\tui\target\murces-tui-1.6.jar`" %*" | Out-File -FilePath ".\murces.bat" -Encoding ascii
        Write-Color "  ✔ Created .\murces.bat launcher wrapper." "Green"
    }
} else {
    if (Ask-Consent "Download and install MurCes native executable from GitHub Releases?" $true) {
        Download-FileWithProgress "https://github.com/DeployedReject/murces/releases/latest/download/murces-windows-amd64.exe" $murcesExe "MurCes Windows Native Executable" | Out-Null
    }
}
Write-Host ""

# Verification Summary
Write-Color "============================================================" "White"
Write-Color "                 Verification & Summary                     " "White"
Write-Color "============================================================" "White"

function Check-Component([string]$name, [string]$desc, [bool]$condition, [bool]$required) {
    $statusText = if ($condition) { "✔ Ready" } elseif ($required) { "✖ Missing (Required)" } else { "○ Optional (Not installed)" }
    $color = if ($condition) { "Green" } elseif ($required) { "Red" } else { "Yellow" }
    Write-Host ("  {0,-12} {1,-38} " -f $name, "($desc)") -NoNewline
    Write-Color $statusText $color
}

Check-Component "murces" "MurCes Native Manager" ((Test-Path ".\murces.exe") -or (Test-Path ".\murces.bat") -or (Get-Command "murces" -ErrorAction SilentlyContinue)) $true
Check-Component "psmux" "Windows Process Multiplexer" ((Get-Command "psmux" -ErrorAction SilentlyContinue) -or (Test-Path ".\psmux.exe")) $true
Check-Component "curl" "HTTP Package Downloader" [bool](Get-Command "curl.exe" -ErrorAction SilentlyContinue) $true
Check-Component "tar" "World Archive Bundler" [bool](Get-Command "tar.exe" -ErrorAction SilentlyContinue) $true
Check-Component "rclone" "Cloud Sync & Google Drive" [bool](Get-Command "rclone" -ErrorAction SilentlyContinue) $false
Check-Component "playit" "Zero-Config Public Tunnels" ([bool](Get-Command "playit" -ErrorAction SilentlyContinue) -or (Test-Path ".\playit.exe")) $false

Write-Host ""
Write-Color "✦ Setup complete!" "Green"
if (Test-Path ".\murces.exe") {
    Write-Host "Launch the interactive dashboard anytime with: " -NoNewline
    Write-Color ".\murces.exe" "Cyan"
} elseif (Test-Path ".\murces.bat") {
    Write-Host "Launch the interactive dashboard anytime with: " -NoNewline
    Write-Color ".\murces.bat" "Cyan"
}
Write-Host ""
