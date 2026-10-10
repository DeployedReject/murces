#!/usr/bin/env bash
# ==============================================================================
#  __  __
# |  \/  |_   _ _ __ ___ ___  ___
# | |\/| | | | | '__/ __/ _ \/ __|
# | |  | | |_| | | | (_|  __/\__ \
# |_|  |_|\__,_|_|  \___\___||___/
#    Minecraft Server Manager - Uninstaller
# ==============================================================================

set -uo pipefail

BOLD='\033[1m'
DIM='\033[2m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
CYAN='\033[0;36m'
MAGENTA='\033[0;35m'
RESET='\033[0m'

echo -e "${CYAN}${BOLD}"
cat << "EOF"
  __  __
 |  \/  |_   _ _ __ ___ ___  ___
 | |\/| | | | | '__/ __/ _ \/ __|
 | |  | | |_| | | | (_|  __/\__ \
 |_|  |_|\__,_|_|  \___\___||___/
EOF
echo -e "${RESET}${DIM}  Minecraft Server Manager — Uninstaller${RESET}"
echo -e "${CYAN}------------------------------------------------------------${RESET}"
echo ""

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
        [yY][eE][sS]|[yY]) return 0 ;;
        *) return 1 ;;
    esac
}

# 1. Locate binary locations
FOUND_BINS=()
SEARCH_PATHS=(
    "$HOME/.local/bin/murces"
    "$HOME/bin/murces"
    "/usr/local/bin/murces"
    "/usr/bin/murces"
)

for p in "${SEARCH_PATHS[@]}"; do
    if [ -f "$p" ]; then
        FOUND_BINS+=("$p")
    fi
done

WHICH_BIN=$(command -v murces 2>/dev/null || true)
if [ -n "$WHICH_BIN" ]; then
    ALREADY_IN=0
    for b in "${FOUND_BINS[@]}"; do
        if [ "$b" = "$WHICH_BIN" ]; then
            ALREADY_IN=1
            break
        fi
    done
    if [ "$ALREADY_IN" -eq 0 ]; then
        FOUND_BINS+=("$WHICH_BIN")
    fi
fi

if [ ${#FOUND_BINS[@]} -eq 0 ]; then
    echo -e "${YELLOW}  ⚠ No murces binary found in standard PATH directories.${RESET}"
else
    echo -e "${BOLD}Found MurCes installation(s):${RESET}"
    for b in "${FOUND_BINS[@]}"; do
        echo -e "  - ${CYAN}${b}${RESET}"
    done
    echo ""

    if ask_consent "Remove MurCes binary executable(s)?" "y"; then
        for b in "${FOUND_BINS[@]}"; do
            if [ -w "$(dirname "$b")" ] || [ -w "$b" ]; then
                rm -f "$b"
                echo -e "${GREEN}  ✔ Removed ${b}${RESET}"
            else
                if command -v sudo >/dev/null 2>&1; then
                    sudo rm -f "$b"
                    echo -e "${GREEN}  ✔ Removed ${b} (via sudo)${RESET}"
                else
                    echo -e "${RED}  ✖ Permission denied removing ${b}${RESET}"
                fi
            fi
        done
    fi
fi
echo ""

# 2. Config & Data cleanup
DATA_DIRS=(
    "$HOME/.murces"
    "$HOME/.config/murces"
)

for d in "${DATA_DIRS[@]}"; do
    if [ -d "$d" ]; then
        if ask_consent "Remove user configuration directory (${d})?" "n"; then
            rm -rf "$d"
            echo -e "${GREEN}  ✔ Removed ${d}${RESET}"
        else
            echo -e "${DIM}  - Kept ${d}${RESET}"
        fi
    fi
done
echo ""

# 3. Clean PATH in shell RC files
if ask_consent "Clean exported PATH entries from shell profiles (~/.bashrc, ~/.zshrc, ~/.profile)?" "y"; then
    for rc in "$HOME/.bashrc" "$HOME/.zshrc" "$HOME/.profile"; do
        if [ -f "$rc" ] && grep -q ".local/bin" "$rc" 2>/dev/null; then
            sed -i '/export PATH="\$PATH:\$HOME\/\.local\/bin"/d' "$rc" 2>/dev/null || true
            sed -i '/export PATH="\$PATH:'"${HOME//\//\\/}"'\/\.local\/bin"/d' "$rc" 2>/dev/null || true
            echo -e "${GREEN}  ✔ Cleaned ${rc}${RESET}"
        fi
    done
fi

echo ""
echo -e "${GREEN}${BOLD}✦ Uninstallation complete!${RESET}"
