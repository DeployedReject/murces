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
    [switch]$Unattended = $false,
    [string]$InstallDir = ""
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
    Write-Color "$prompt ${suffix}: " "White" -NoNewline
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
$rawArch = $env:PROCESSOR_ARCHITECTURE
if (-not $rawArch) {
    try {
        $rawArch = [System.Runtime.InteropServices.RuntimeInformation]::OSArchitecture.ToString()
    } catch {
        $rawArch = if ([Environment]::Is64BitOperatingSystem) { "AMD64" } else { "x86" }
    }
}
$rawArch = $rawArch.ToLower()
if ($rawArch -eq "amd64" -or $rawArch -eq "x64") {
    $rawArch = "amd64"
} elseif ($rawArch -eq "arm64") {
    $rawArch = "arm64"
} else {
    $rawArch = "x86"
}
$archLabel = "Windows $rawArch"
Write-Color "✦ Architecture detected: $archLabel" "Magenta"

# Determine InstallDir early so helper binaries (playit, psmux) land in a single PATH directory
if ([string]::IsNullOrWhiteSpace($InstallDir)) {
    if ($env:LOCALAPPDATA) {
        $InstallDir = Join-Path $env:LOCALAPPDATA "MurCes"
    } else {
        $InstallDir = Join-Path $HOME ".murces"
    }
}
if (-not (Test-Path $InstallDir)) {
    New-Item -ItemType Directory -Path $InstallDir -Force | Out-Null
}

# Detect Windows Package Managers
$hasWinget = [bool](Get-Command "winget" -ErrorAction SilentlyContinue)
$hasScoop = [bool](Get-Command "scoop" -ErrorAction SilentlyContinue)
$hasChoco = [bool](Get-Command "choco" -ErrorAction SilentlyContinue)

$detectedPm = if ($hasWinget) { "winget" } elseif ($hasScoop) { "scoop" } elseif ($hasChoco) { "choco" } else { "none" }
Write-Color "✦ Package manager detected: $detectedPm" "Magenta"
Write-Host ""

# 2. Multiplexer Installation (psmux for Windows)
Write-Color "[1/4] Terminal Multiplexer (psmux)" "White"
$hasPsmux = [bool](Get-Command "psmux" -ErrorAction SilentlyContinue) -or (Test-Path (Join-Path $InstallDir "psmux.exe"))

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
            $psmuxZip = Join-Path $InstallDir "psmux.zip"
            if (Download-FileWithProgress $psmuxUrl $psmuxZip "psmux release zip") {
                Expand-Archive -Path $psmuxZip -DestinationPath $InstallDir -Force
                Remove-Item $psmuxZip -Force
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
            $rcloneZip = Join-Path $InstallDir "rclone.zip"
            $rcloneDist = Join-Path $InstallDir "rclone-dist"
            Download-FileWithProgress "https://downloads.rclone.org/rclone-current-windows-amd64.zip" $rcloneZip "rclone package"
            Expand-Archive -Path $rcloneZip -DestinationPath $rcloneDist -Force
            Get-ChildItem -Path $rcloneDist -Recurse -Filter "rclone.exe" | Copy-Item -Destination (Join-Path $InstallDir "rclone.exe")
            Remove-Item $rcloneZip, $rcloneDist -Recurse -Force
        }
    } else {
        Write-Color "  - Skipped rclone installation." "DarkGray"
    }
}
Write-Host ""

# 4. Public Tunnels (playit.gg)
Write-Color "[3/4] Public Tunnels (playit.gg)" "White"
$playitExe = Join-Path $InstallDir "playit.exe"
$hasPlayit = [bool](Get-Command "playit" -ErrorAction SilentlyContinue) -or (Test-Path $playitExe) -or (Test-Path ".\playit.exe")

if ($hasPlayit) {
    Write-Color "  ✔ playit tunnel agent is already available." "Green"
} else {
    if (Ask-Consent "Install playit client binary for zero-config public tunnels?" $true) {
        $playitUrl = "https://github.com/playit-cloud/playit-agent/releases/latest/download/playit-windows-x86_64.exe"
        Download-FileWithProgress $playitUrl $playitExe "playit tunnel agent (Windows x64)" | Out-Null
    } else {
        Write-Color "  - Skipped playit setup." "DarkGray"
    }
}
Write-Host ""

# 5. MurCes Standalone Executable
Write-Color "[4/4] MurCes Standalone Executable" "White"
$murcesExe = Join-Path $InstallDir "murces.exe"
Write-Color "  Install location: $InstallDir" "Cyan"

if (Test-Path $murcesExe) {
    Write-Color "  ✔ murces.exe is already present in $InstallDir." "Green"
    if (Ask-Consent "Re-download and overwrite with the latest release from GitHub?" $false) {
        Download-FileWithProgress "https://github.com/DeployedReject/murces/releases/latest/download/murces-windows-amd64.exe" $murcesExe "MurCes Windows Native Executable" | Out-Null
    }
} elseif (Test-Path "java\tui\target\murces.exe") {
    if (Ask-Consent "Deploy locally built native binary 'java\tui\target\murces.exe' to $murcesExe?" $true) {
        Copy-Item "java\tui\target\murces.exe" $murcesExe -Force
        Write-Color "  ✔ Local murces.exe deployed to $murcesExe successfully." "Green"
    }
} elseif (Test-Path "java\tui\target\murces-tui-1.6.jar") {
    $murcesBat = Join-Path $InstallDir "murces.bat"
    if (Ask-Consent "Create murces.bat launcher for locally built JAR in $InstallDir?" $true) {
        $jarPath = (Resolve-Path "java\tui\target\murces-tui-1.6.jar").Path
        "@echo off`nchcp 65001 >nul`njava -Dfile.encoding=UTF-8 -jar `"$jarPath`" %*" | Out-File -FilePath $murcesBat -Encoding ascii
        Write-Color "  ✔ Created $murcesBat launcher wrapper." "Green"
    }
} else {
    if (Ask-Consent "Download and install MurCes native executable to $InstallDir?" $true) {
        Download-FileWithProgress "https://github.com/DeployedReject/murces/releases/latest/download/murces-windows-amd64.exe" $murcesExe "MurCes Windows Native Executable" | Out-Null
    }
}

# Create companion murces.cmd helper in InstallDir to ensure chcp 65001 before executing murces.exe
$murcesCmd = Join-Path $InstallDir "murces.cmd"
if (Test-Path $murcesExe) {
    "@echo off`nchcp 65001 >nul`n`"%~dp0murces.exe`" %*" | Out-File -FilePath $murcesCmd -Encoding ascii
}

# Ensure InstallDir is added to User PATH environment variable
$pathUpdated = $false
try {
    $currentPath = [Environment]::GetEnvironmentVariable("Path", [EnvironmentVariableTarget]::User)
    $pathParts = if ($currentPath) { $currentPath -split ";" } else { @() }
    if ($pathParts -notcontains $InstallDir) {
        $newPath = ($pathParts + $InstallDir) -join ";"
        [Environment]::SetEnvironmentVariable("Path", $newPath, [EnvironmentVariableTarget]::User)
        $env:Path = "$env:Path;$InstallDir"
        $pathUpdated = $true
        Write-Color "  ✔ Added $InstallDir to user PATH environment variable." "Green"
    }
} catch {
    # Non-fatal
}

# Ensure Windows Virtual Terminal Processing is enabled in Registry
try {
    $consoleReg = "HKCU:\Console"
    $vtLevel = (Get-ItemProperty -Path $consoleReg -Name "VirtualTerminalLevel" -ErrorAction SilentlyContinue).VirtualTerminalLevel
    if ($vtLevel -ne 1) {
        Set-ItemProperty -Path $consoleReg -Name "VirtualTerminalLevel" -Value 1 -Type DWord -Force | Out-Null
        Write-Color "  ✔ Enabled Windows Virtual Terminal (ANSI/VT100) processing." "Green"
    }
} catch {
    # Non-fatal
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

$hasMurcesReady = (Test-Path $murcesExe) -or (Test-Path (Join-Path $InstallDir "murces.bat")) -or [bool](Get-Command "murces" -ErrorAction SilentlyContinue)
Check-Component -name "murces" -desc "MurCes Native Manager" -condition $hasMurcesReady -required $true
Check-Component -name "psmux" -desc "Windows Process Multiplexer" -condition ([bool]((Get-Command "psmux" -ErrorAction SilentlyContinue) -or (Test-Path (Join-Path $InstallDir "psmux.exe")) -or (Test-Path ".\psmux.exe"))) -required $true
Check-Component -name "curl" -desc "HTTP Package Downloader" -condition ([bool](Get-Command "curl.exe" -ErrorAction SilentlyContinue)) -required $true
Check-Component -name "tar" -desc "World Archive Bundler" -condition ([bool](Get-Command "tar.exe" -ErrorAction SilentlyContinue)) -required $true
Check-Component -name "rclone" -desc "Cloud Sync & Google Drive" -condition ([bool]((Get-Command "rclone" -ErrorAction SilentlyContinue) -or (Test-Path (Join-Path $InstallDir "rclone.exe")))) -required $false
Check-Component -name "playit" -desc "Zero-Config Public Tunnels" -condition ([bool]((Get-Command "playit" -ErrorAction SilentlyContinue) -or (Test-Path (Join-Path $InstallDir "playit.exe")) -or (Test-Path ".\playit.exe"))) -required $false

Write-Host ""
Write-Color "✦ Setup complete!" "Green"
if (Test-Path $murcesExe) {
    Write-Color "Installed to: $murcesExe" "Cyan"
    Write-Host "Launch the interactive dashboard anywhere with: " -NoNewline
    Write-Color "murces" "Cyan"
    if ($pathUpdated) {
        Write-Color "Note: Open a new terminal window to use 'murces' directly from any folder." "DarkGray"
    }
} elseif (Test-Path (Join-Path $InstallDir "murces.bat")) {
    Write-Color "Installed to: $(Join-Path $InstallDir 'murces.bat')" "Cyan"
    Write-Host "Launch the interactive dashboard with: " -NoNewline
    Write-Color "murces" "Cyan"
}
Write-Host ""
