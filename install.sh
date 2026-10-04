#!/usr/bin/env bash
# ==============================================================================
#  __  __
# |  \/  |_   _ _ __ ___ ___  ___
# | |\/| | | | | '__/ __/ _ \/ __|
# | |  | | |_| | | | (_|  __/\__ \
# |_|  |_|\__,_|_|  \___\___||___/
#    Minecraft Server Manager - Installer
#
# Automatically installs runtime dependencies and MurCes native executable:
# - System Packages: tmux, curl, tar, OpenJDK (Java 21/17)
# - Optional Tools: rclone (Google Drive sync), playit (public tunnels)
# - MurCes Binary: Fetches latest standalone Linux ELF from GitHub Releases
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
    # Kill background spinner if still alive
    if [ -n "${SPINNER_PID:-}" ] && kill -0 "$SPINNER_PID" 2>/dev/null; then
        kill "$SPINNER_PID" 2>/dev/null || true
        wait "$SPINNER_PID" 2>/dev/null || true
    fi
}
trap cleanup EXIT INT TERM

# Beautiful header
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

# Spinner animation for long-running shell actions
# Usage: run_with_spinner "Action description" command args...
run_with_spinner() {
    local label="$1"
    shift
    local log_file
    log_file=$(mktemp)

    echo -ne "${CYAN}  ⠋${RESET} ${label}..."

    # Run command in background redirecting output
    "$@" >"$log_file" 2>&1 &
    local cmd_pid=$!

    local spin_chars=('⠋' '⠙' '⠹' '⠸' '⠼' '⠴' '⠦' '⠧' '⠇' '⠏')
    local i=0

    echo -ne "$HIDE_CURSOR"
    while kill -0 "$cmd_pid" 2>/dev/null; do
        local frame="${spin_chars[i % ${#spin_chars[@]}]}"
        echo -ne "\r${CYAN}  ${frame}${RESET} ${label}... "
        i=$((i + 1))
        sleep 0.08
    done

    wait "$cmd_pid"
    local exit_code=$?

    if [ "$exit_code" -eq 0 ]; then
        echo -ne "\r${GREEN}  ✔${RESET} ${label} ${DIM}(done)${RESET}\n"
        rm -f "$log_file"
        return 0
    else
        echo -ne "\r${RED}  ✖${RESET} ${label} ${RED}(failed with code ${exit_code})${RESET}\n"
        if [ -s "$log_file" ]; then
            echo -e "${DIM}"
            tail -n 8 "$log_file" | sed 's/^/    | /'
            echo -e "${RESET}"
        fi
        rm -f "$log_file"
        return "$exit_code"
    fi
}

# Modern block progress bar for file downloads
# Usage: download_with_progress "URL" "Destination" "Label"
download_with_progress() {
    local url="$1"
    local dest="$2"
    local label="$3"

    echo -e "${BLUE}  ⬇${RESET} ${BOLD}${label}${RESET}"
    echo -ne "$HIDE_CURSOR"

    # Use curl with a custom modern progress meter
    local temp_dest="${dest}.tmp"
    rm -f "$temp_dest"

    if command -v curl >/dev/null 2>&1; then
        # Check if terminal supports width, default 60 cols
        local term_width
        term_width=$(tput cols 2>/dev/null || echo 80)
        local bar_width=32
        if [ "$term_width" -lt 60 ]; then
            bar_width=18
        fi

        # Run curl in background feeding progress info via status line
        curl -sSL -L --fail --progress-bar -o "$temp_dest" "$url" 2>&1 | while IFS= read -r -d $'\r' line; do
            # Extract percentage from curl's progress bar output
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

# Determine privilege elevation tool
SUDO=""
if [ "$(id -u)" -ne 0 ]; then
    if command -v sudo >/dev/null 2>&1; then
        SUDO="sudo"
    elif command -v doas >/dev/null 2>&1; then
        SUDO="doas"
    else
        echo -e "${RED}[ERROR] Root privileges required to install system packages. Please install sudo or run as root.${RESET}"
        exit 1
    fi
fi

# Detect package manager
detect_pm() {
    if command -v apt-get >/dev/null 2>&1; then
        echo "apt"
    elif command -v pacman >/dev/null 2>&1; then
        echo "pacman"
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

install_packages() {
    local pm="$1"
    case "$pm" in
        apt)
            $SUDO apt-get update -qq -y
            $SUDO apt-get install -qq -y tmux curl tar openjdk-21-jre-headless rclone 2>/dev/null || \
            $SUDO apt-get install -qq -y tmux curl tar openjdk-17-jre-headless rclone 2>/dev/null || \
            $SUDO apt-get install -qq -y tmux curl tar default-jre-headless rclone
            ;;
        pacman)
            $SUDO pacman -Sy --noconfirm --needed tmux curl tar jre21-openjdk-headless rclone 2>/dev/null || \
            $SUDO pacman -Sy --noconfirm --needed tmux curl tar jre-openjdk-headless rclone
            ;;
        dnf)
            $SUDO dnf install -y -q tmux curl tar java-21-openjdk-headless rclone 2>/dev/null || \
            $SUDO dnf install -y -q tmux curl tar java-17-openjdk-headless rclone
            ;;
        yum)
            $SUDO yum install -y -q tmux curl tar java-17-openjdk-headless rclone
            ;;
        zypper)
            $SUDO zypper --quiet --non-interactive install tmux curl tar java-21-openjdk-headless rclone 2>/dev/null || \
            $SUDO zypper --quiet --non-interactive install tmux curl tar java-17-openjdk-headless rclone
            ;;
        apk)
            $SUDO apk update -q
            $SUDO apk add -q tmux curl tar openjdk21-jre-headless rclone 2>/dev/null || \
            $SUDO apk add -q tmux curl tar openjdk17-jre-headless rclone
            ;;
        *)
            return 1
            ;;
    esac
}

# Main execution flow
print_banner

PM=$(detect_pm)
echo -e "${MAGENTA}✦${RESET} Detected Linux environment: ${BOLD}${PM}${RESET}"
echo ""

# 1. System Dependencies Installation
echo -e "${BOLD}[1/3] System Dependencies${RESET}"
if [ "$PM" != "unknown" ]; then
    run_with_spinner "Updating repositories & installing packages (tmux, OpenJDK, curl, tar, rclone)" install_packages "$PM" || true
else
    echo -e "${YELLOW}  ⚠ Unrecognized package manager. Skipping package install.${RESET}"
fi
echo ""

# 2. Public Tunnel (playit.gg)
echo -e "${BOLD}[2/3] Public Tunnels (playit.gg)${RESET}"
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
        echo -ne "${YELLOW}  ?${RESET} Install playit binary for zero-config public tunneling? [Y/n]: "
        read -r choice || choice="y"
        choice=${choice:-y}
        case "$choice" in
            [yY][eE][sS]|[yY])
                download_with_progress "$PLAYIT_URL" "playit" "playit tunnel agent ($ARCH)"
                chmod +x playit 2>/dev/null || true
                ;;
            *)
                echo -e "${DIM}  - Skipped playit setup.${RESET}"
                ;;
        esac
    fi
fi
echo ""

# 3. MurCes Native Executable Installation
echo -e "${BOLD}[3/3] MurCes Standalone Executable${RESET}"
MURCES_BIN="./murces"
RELEASE_URL="https://github.com/DeployedReject/murces/releases/latest/download/murces"

# Check if local pre-built binary is already present
if [ -f "java/tui/target/murces" ] && [ ! -f "$MURCES_BIN" ]; then
    echo -e "${CYAN}  ℹ${RESET} Copying locally built binary..."
    cp "java/tui/target/murces" "$MURCES_BIN"
    chmod +x "$MURCES_BIN"
    echo -e "${GREEN}  ✔${RESET} Deployed local murces executable."
else
    download_with_progress "$RELEASE_URL" "$MURCES_BIN" "MurCes native binary (Linux x86_64)"
    chmod +x "$MURCES_BIN" 2>/dev/null || true
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
check_tool "java" "OpenJDK Runtime" "req"
check_tool "curl" "HTTP Package Downloader" "req"
check_tool "tar" "World Archive Bundler" "req"
check_tool "rclone" "Cloud Sync & Google Drive" "opt"
check_tool "playit" "Zero-Config Public Tunnels" "opt"

echo ""
echo -e "${GREEN}${BOLD}✦ Setup finished successfully!${RESET}"
echo -e "Launch the interactive dashboard anytime with: ${BOLD}${CYAN}./murces${RESET}"
echo ""
