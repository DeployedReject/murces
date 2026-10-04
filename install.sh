#!/usr/bin/env bash
# ==============================================================================
#  __  __
# |  \/  |_   _ _ __ ___ ___  ___
# | |\/| | | | | '__/ __/ _ \/ __|
# | |  | | |_| | | | (_|  __/\__ \
# |_|  |_|\__,_|_|  \___\___||___/
#    Minecraft Server Manager - Installer
#
# Interactive, consent-driven installer for MurCes:
# - Authenticates sudo at startup before executing installation tasks
# - Asks for user consent before installing each package / tool
# - Shows real-time progress for every package and download
# - Installs core packages: tmux, curl, tar, rclone, playit, and MurCes binary
# - NOTE: Does NOT install JDK / Java runtime
# ==============================================================================

set -uo pipefail

# ANSI color codes & styles
BOLD='\033[1m'
DIM='\033[2m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
CYAN='\033[0;36m'
BLUE='\033[0;34m'
MAGENTA='\033[0;35m'
RESET='\033[0m'

# Terminal cursor controls
HIDE_CURSOR='\033[?25l'
SHOW_CURSOR='\033[?25h'

cleanup() {
    echo -ne "$SHOW_CURSOR"
    # Kill background sudo keep-alive or spinners if still alive
    if [ -n "${SUDO_KEEP_ALIVE_PID:-}" ] && kill -0 "$SUDO_KEEP_ALIVE_PID" 2>/dev/null; then
        kill "$SUDO_KEEP_ALIVE_PID" 2>/dev/null || true
    fi
}
trap cleanup EXIT INT TERM

# Beautiful banner
print_banner() {
    clear 2>/dev/null || true
    echo -e "${CYAN}${BOLD}"
    cat << "EOF"
  __  __
 |  \/  |_   _ _ __ ___ ___  ___
 | |\/| | | | | '__/ __/ _ \/ __|
 | |  | | |_| | | | (_|  __/\__ \
 |_|  |_|\__,_|_|  \___\___||___/
EOF
    echo -e "${RESET}${DIM}  Minecraft Server Manager — Automated Setup${RESET}"
    echo -e "${CYAN}------------------------------------------------------------${RESET}"
    echo ""
}

print_banner

# Prompt for sudo privileges upfront before doing anything
SUDO=""
if [ "$(id -u)" -ne 0 ]; then
    if command -v sudo >/dev/null 2>&1; then
        SUDO="sudo"
        echo -e "${YELLOW}${BOLD}Authentication Required${RESET}"
        echo -e "${DIM}This installer requires administrator privileges to manage system packages.${RESET}"
        echo -e "${DIM}Please enter your sudo password now to proceed:${RESET}"
        echo ""

        # Validate sudo credentials upfront
        if ! sudo -v; then
            echo -e "${RED}[ERROR] Sudo authentication failed. Aborting installation.${RESET}"
            exit 1
        fi

        # Keep sudo timestamp updated in background for uninterrupted execution
        while true; do
            sudo -n true
            sleep 50
            kill -0 "$$" || exit
        done 2>/dev/null &
        SUDO_KEEP_ALIVE_PID=$!
        echo -e "${GREEN}✔ Sudo authentication successful.${RESET}"
        echo ""
    elif command -v doas >/dev/null 2>&1; then
        SUDO="doas"
    else
        echo -e "${RED}[ERROR] Root privileges required to install packages. Please install sudo or run as root.${RESET}"
        exit 1
    fi
fi

# Detect package manager
detect_pm() {
    if command -v pacman >/dev/null 2>&1; then
        echo "pacman"
    elif command -v apt-get >/dev/null 2>&1; then
        echo "apt"
    elif command -v dnf >/dev/null 2>&1; then
        echo "dnf"
    elif command -v yum >/dev/null 2>&1; then
        echo "yum"
    elif command -v zypper >/dev/null 2>&1; then
        echo "zypper"
    elif command -v apk >/dev/null 2>&1; then
        echo "apk"
    else
        echo "unknown"
    fi
}

PM=$(detect_pm)
echo -e "${MAGENTA}✦${RESET} Package manager detected: ${BOLD}${PM}${RESET}"
echo ""

# Helper to read user input even when piped from curl (reads from /dev/tty if available)
ask_consent() {
    local prompt="$1"
    local default_val="${2:-y}"
    local reply=""

    if [ "$default_val" = "y" ]; then
        echo -ne "${YELLOW}  ?${RESET} ${prompt} [Y/n]: "
    else
        echo -ne "${YELLOW}  ?${RESET} ${prompt} [y/N]: "
    fi

    if [ -t 0 ]; then
        read -r reply || reply=""
    elif [ -r /dev/tty ]; then
        read -r reply </dev/tty || reply=""
    else
        reply="$default_val"
        echo "$reply"
    fi

    reply="${reply:-$default_val}"
    case "$reply" in
        [yY][eE][sS]|[yY])
            return 0
            ;;
        *)
            return 1
            ;;
    esac
}

# Modern block progress bar for downloads
download_with_progress() {
    local url="$1"
    local dest="$2"
    local label="$3"

    echo -e "${BLUE}  ⬇${RESET} ${BOLD}${label}${RESET}"
    echo -ne "$HIDE_CURSOR"

    local temp_dest="${dest}.tmp"
    rm -f "$temp_dest"

    if command -v curl >/dev/null 2>&1; then
        local term_width
        term_width=$(tput cols 2>/dev/null || echo 80)
        local bar_width=32
        if [ "$term_width" -lt 60 ]; then
            bar_width=18
        fi

        curl -sSL -L --fail --progress-bar -o "$temp_dest" "$url" 2>&1 | while IFS= read -r -d $'\r' line; do
            local percent
            percent=$(echo "$line" | grep -oE '[0-9]+(\.[0-9]+)?%' | tr -d '%' | head -n 1)
            if [ -n "$percent" ]; then
                local int_p=${percent%.*}
                int_p=${int_p:-0}
                if [ "$int_p" -gt 100 ]; then int_p=100; fi

                local filled=$(( (int_p * bar_width) / 100 ))
                local empty=$(( bar_width - filled ))
                local bar_filled
                local bar_empty
                bar_filled=$(printf "%${filled}s" | tr ' ' '█')
                bar_empty=$(printf "%${empty}s" | tr ' ' '░')

                printf "\r    ${CYAN}[${GREEN}%s${DIM}%s${CYAN}]${RESET} %3d%%" "$bar_filled" "$bar_empty" "$int_p"
            fi
        done
        echo ""

        if [ -f "$temp_dest" ] && [ -s "$temp_dest" ]; then
            mv -f "$temp_dest" "$dest"
            echo -e "${GREEN}    ✔ Download complete:${RESET} ${DIM}${dest}${RESET}"
            return 0
        else
            echo -e "${RED}    ✖ Download failed or returned empty file.${RESET}"
            rm -f "$temp_dest"
            return 1
        fi
    else
        echo -e "${RED}  ✖ curl is not available.${RESET}"
        return 1
    fi
}

# Install single system package using native package manager with live progress
install_single_package() {
    local pkg="$1"
    local label="$2"
    local required="$3" # "req" or "opt"

    # Check if already installed
    if command -v "$pkg" >/dev/null 2>&1; then
        echo -e "${GREEN}  ✔${RESET} ${BOLD}${pkg}${RESET} (${label}) is already installed."
        return 0
    fi

    local req_text="${GREEN}[Required]${RESET}"
    if [ "$required" = "opt" ]; then
        req_text="${DIM}[Optional]${RESET}"
    fi

    if ! ask_consent "Install ${BOLD}${pkg}${RESET} (${label}) ${req_text}?" "y"; then
        echo -e "${DIM}    - Skipped ${pkg}.${RESET}"
        return 0
    fi

    echo -e "${CYAN}  ➔ Installing ${BOLD}${pkg}${RESET} via ${PM}..."

    local exit_code=0
    case "$PM" in
        pacman)
            $SUDO pacman -S --needed --noconfirm "$pkg" || exit_code=$?
            ;;
        apt)
            $SUDO apt-get install -y "$pkg" || exit_code=$?
            ;;
        dnf)
            $SUDO dnf install -y "$pkg" || exit_code=$?
            ;;
        yum)
            $SUDO yum install -y "$pkg" || exit_code=$?
            ;;
        zypper)
            $SUDO zypper --non-interactive install "$pkg" || exit_code=$?
            ;;
        apk)
            $SUDO apk add "$pkg" || exit_code=$?
            ;;
        *)
            echo -e "${YELLOW}  ⚠ Unrecognized package manager. Please install ${pkg} manually.${RESET}"
            return 1
            ;;
    esac

    if [ "$exit_code" -eq 0 ]; then
        echo -e "${GREEN}  ✔ ${pkg} installed successfully.${RESET}"
    else
        echo -e "${RED}  ✖ Failed to install ${pkg} (exit code ${exit_code}).${RESET}"
    fi
    echo ""
}

# 1. Update Repositories
echo -e "${BOLD}[1/4] Repository Index Sync${RESET}"
if [ "$PM" != "unknown" ]; then
    if ask_consent "Sync and update package repositories?" "y"; then
        echo -e "${CYAN}  ➔ Updating package repositories...${RESET}"
        case "$PM" in
            pacman) $SUDO pacman -Sy ;;
            apt)    $SUDO apt-get update -y ;;
            apk)    $SUDO apk update ;;
            *)      true ;;
        esac
        echo -e "${GREEN}  ✔ Package database synchronized.${RESET}"
    else
        echo -e "${DIM}  - Skipped repository refresh.${RESET}"
    fi
fi
echo ""

# 2. Individual Package Installation (Consented with live download progress)
echo -e "${BOLD}[2/4] Package Dependencies (JDK skipped)${RESET}"
install_single_package "tmux" "Detached Process Multiplexer" "req"
install_single_package "curl" "HTTP Downloader" "req"
install_single_package "tar" "World Archive Bundler" "req"
install_single_package "rclone" "Google Drive & Cloud Sync" "opt"
echo ""

# 3. Public Tunnels (playit.gg)
echo -e "${BOLD}[3/4] Public Tunnels (playit.gg)${RESET}"
if command -v playit >/dev/null 2>&1 || [ -x "./playit" ]; then
    echo -e "${GREEN}  ✔${RESET} playit CLI is already installed."
else
    ARCH=$(uname -m)
    PLAYIT_URL=""
    case "$ARCH" in
        x86_64)
            PLAYIT_URL="https://github.com/playit-cloud/playit-agent/releases/latest/download/playit-linux-amd64"
            ;;
        aarch64|arm64)
            PLAYIT_URL="https://github.com/playit-cloud/playit-agent/releases/latest/download/playit-linux-aarch64"
            ;;
    esac

    if [ -n "$PLAYIT_URL" ]; then
        if ask_consent "Install playit client binary for zero-config public tunnels?" "y"; then
            download_with_progress "$PLAYIT_URL" "playit" "playit tunnel agent ($ARCH)"
            chmod +x playit 2>/dev/null || true
        else
            echo -e "${DIM}  - Skipped playit setup.${RESET}"
        fi
    fi
fi
echo ""

# 4. MurCes Standalone Executable Installation
echo -e "${BOLD}[4/4] MurCes Standalone Executable${RESET}"
MURCES_BIN="./murces"
RELEASE_URL="https://github.com/DeployedReject/murces/releases/latest/download/murces"

if [ -f "$MURCES_BIN" ] && [ -x "$MURCES_BIN" ]; then
    echo -e "${GREEN}  ✔${RESET} ./murces executable is already present in this directory."
    if ask_consent "Re-download and overwrite with the latest release from GitHub?" "n"; then
        download_with_progress "$RELEASE_URL" "$MURCES_BIN" "MurCes native binary (Linux x86_64)"
        chmod +x "$MURCES_BIN" 2>/dev/null || true
    fi
elif [ -f "java/tui/target/murces" ]; then
    if ask_consent "Deploy locally built native binary 'java/tui/target/murces' to ./murces?" "y"; then
        cp "java/tui/target/murces" "$MURCES_BIN"
        chmod +x "$MURCES_BIN"
        echo -e "${GREEN}  ✔ Local murces binary deployed successfully.${RESET}"
    fi
else
    if ask_consent "Download and install MurCes native executable from GitHub Releases?" "y"; then
        download_with_progress "$RELEASE_URL" "$MURCES_BIN" "MurCes native binary (Linux x86_64)"
        chmod +x "$MURCES_BIN" 2>/dev/null || true
    fi
fi
echo ""

# Verification Summary
echo -e "${BOLD}============================================================${RESET}"
echo -e "${BOLD}                 Verification & Summary                     ${RESET}"
echo -e "${BOLD}============================================================${RESET}"

check_tool() {
    local cmd="$1"
    local desc="$2"
    local req="$3"

    if command -v "$cmd" >/dev/null 2>&1; then
        printf "  %-12s %-38s ${GREEN}✔ Ready${RESET}\n" "$cmd" "($desc)"
    elif [ "$cmd" = "playit" ] && [ -x "./playit" ]; then
        printf "  %-12s %-38s ${GREEN}✔ Ready (local)${RESET}\n" "./playit" "($desc)"
    elif [ "$cmd" = "murces" ] && [ -x "./murces" ]; then
        printf "  %-12s %-38s ${GREEN}✔ Ready (local)${RESET}\n" "./murces" "($desc)"
    else
        if [ "$req" = "req" ]; then
            printf "  %-12s %-38s ${RED}✖ Missing (Required)${RESET}\n" "$cmd" "($desc)"
        else
            printf "  %-12s %-38s ${YELLOW}○ Optional (Not installed)${RESET}\n" "$cmd" "($desc)"
        fi
    fi
}

check_tool "murces" "MurCes Native Manager" "req"
check_tool "tmux" "Detached Process Supervision" "req"
check_tool "curl" "HTTP Package Downloader" "req"
check_tool "tar" "World Archive Bundler" "req"
check_tool "rclone" "Cloud Sync & Google Drive" "opt"
check_tool "playit" "Zero-Config Public Tunnels" "opt"

echo ""
echo -e "${GREEN}${BOLD}✦ Setup complete!${RESET}"
if [ -x "./murces" ]; then
    echo -e "Launch the interactive dashboard anytime with: ${BOLD}${CYAN}./murces${RESET}"
else
    echo -e "You can launch MurCes once the executable is present."
fi
echo ""
