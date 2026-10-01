# MurCes

```
  __  __                                
 |  \/  |_   _ _ __ ___ ___  ___        
 | |\/| | | | | '__/ __/ _ \/ __|       
 | |  | | |_| | | | (_|  __/\__ \       
 |_|  |_|\__,_|_|  \___\___||___/       
    Minecraft Server Manager v1.0.0     
```

MurCes is a terminal manager for dedicated Minecraft servers on Linux. It combines a terminal interface (TUI) and simple CLI flags to handle server installs, configuration tweaks, mod management, and background session controls without having to run heavy web panels like Pterodactyl or AMP.

It is built in Java using Lanterna and compiled into a standalone native binary with GraalVM Native Image, giving it near-instant startup and small memory overhead for low-resource VPS nodes and older machines.

---

## Branches

- **`main`**: Active development branch containing the Lanterna TUI, orchestrator backend, and GraalVM build configuration.
- **`archived`**: Legacy C prototype and precursor library codebase kept for reference.

---

## What It Does

- **Engine Setup**: Installs and sets up Paper, Fabric, Forge, Spigot, or Vanilla across chosen Minecraft versions, automatically accepting the Mojang EULA.
- **Mod Browser**: Queries Modrinth and CurseForge APIs to find mods matching your server loader (Fabric, Forge, NeoForge, Quilt) and version, downloading `.jar` files straight to `mods/`.
- **Runtime & Console**: Runs servers inside detached `tmux` sessions. Lets you send in-game console commands directly from the interface and monitor log output.
- **Network Tunneling**: Optional integration with Playit.gg (`--public` / `-p`) to open servers to friends without manual port forwarding.
- **Settings Editor**: Searchable editor for `server.properties` with categorized key-value edits.
- **World Backups**: Safe tar archive snapshots that flush world data first (`save-all`, `save-off`, `save-on`), with retention limits and optional `rclone` cloud sync.
- **Player Migration**: Utility to transfer playerdata, stats, advancements, and usercache between UUIDs.

---

## Quickstart

Download the latest `murces.zip` from [Releases](https://github.com/DeployedReject/murces/releases) and extract it into your server folder:

```sh
unzip murces.zip
chmod +x murces svctrl.sh backup.sh
./murces
```

### CLI Subcommands
You can also run commands directly from the shell without opening the full menu:

```sh
./murces start [-p]    # Start server (-p / --public enables Playit.gg)
./murces stop          # Stop server
./murces status        # Check if the server is running
./murces backup        # Run a world backup
./murces --test-tui    # Headless test across all interface screens
./murces --help        # Show help options
./murces --version     # Print version
```

---

## Requirements

- **OS**: Linux 64-bit (or WSL on Windows 10+)
- **System packages**: `tmux` (required for background sessions), `curl` or `wget`
- **Optional**: `rclone` (for cloud sync backups), `playit` (for public tunneling)
- **Java runtime**: Java 21+ or GraalVM only if compiling from source. The native binary runs standalone without a JDK installed.

---

## Fonts & Terminal Rendering

MurCes uses [Nerd Font](https://www.nerdfonts.com/) glyphs to provide clean icons for navigation, server status indicators, and mod loading animations.

### Easy Font Installation (`getnf`)
To easily install any Nerd Font on Linux or macOS, you can use [**`getnf`**](https://github.com/getnf/getnf), an open-source tool that lets you browse and install fonts in seconds:

```sh
# Install getnf
curl -fsSL https://raw.githubusercontent.com/getnf/getnf/main/install.sh | bash

# Run getnf to pick and install a font (e.g. JetBrainsMono, FiraCode, Hack)
getnf
```

You can also download fonts directly from [Nerd Fonts Downloads](https://www.nerdfonts.com/font-downloads).

### Automatic Fallback (No Nerd Font Required)
If you do not have a Nerd Font installed or are running in a basic terminal/TTY, **MurCes automatically detects this and falls back to clean basic text rendering** (ASCII/basic Unicode). Zero missing glyph boxes or broken characters.

You can also control glyph rendering manually:
- **In-App**: Press `[Z]` or select `[Z] Customization & Themes`, then set `[G]lyphs` to `Auto-detect`, `Force Nerd Fonts`, or `Basic (Fallback)`.
- **Environment Flags**: Run with `NO_NERD_FONT=1` to force basic fallback mode, or `FORCE_NERD_FONT=1` to force Nerd Fonts.

---

## Building from Source

If you want to build the project yourself instead of using the precompiled release:

### 1. Build Orchestrator
```sh
cd java/orchestrator
mvn clean install
cd ../..
```

### 2. Build TUI (JAR)
```sh
cd java/tui
mvn clean package
# Produces java/tui/target/murces-tui-0.1.jar
```

### 3. Build Native Binary (GraalVM)
```sh
cd java/tui
mvn clean package -Pnative
cp target/murces ../../
```

---

## Using MurCes Orchestrator in Your Own Projects

**MurCes Orchestrator** (`murces-orchestrator`) is designed as a standalone, decoupled backend service. It provides a standardized JSON-based IPC interface over standard I/O (`stdin`/`stdout`), allowing you to build your own custom UIs, web panels, Discord bots, scripts, or remote management tools on top of it.

### Capabilities
- **Server Lifecycle**: Automated downloading, setup, EULA acceptance, and `tmux` session management for Fabric, Forge, Paper, Spigot, and Vanilla.
- **Unified Modding**: One consistent API to query and download mods across both Modrinth and CurseForge.
- **Atomic Operations**: Safe chunked downloads with `.tmp` staging, live progress telemetry, and graceful error handling.

### Running Standalone
Compile the orchestrator jar:
```sh
cd java/orchestrator
mvn clean package
# Binary located at java/orchestrator/target/murces-orchestrator-1.0.jar
```

Run it directly from the terminal or spawn it as a child process:
```sh
java -jar java/orchestrator/target/murces-orchestrator-1.0.jar
```

### Quick IPC Examples

#### Bash / Shell
Pipe JSON requests directly into stdin:

```sh
# Query supported server engines
echo '{"type": "server", "serverType": "none", "gameVersion": "none", "loaderVersion": "none", "ram": 0, "job": 3}' | java -jar murces-orchestrator-1.0.jar

# Install and launch Paper 1.20.4 with 4GB RAM
echo '{"type": "server", "serverType": "paper", "gameVersion": "1.20.4", "loaderVersion": "none", "ram": 4, "job": 1}' | java -jar murces-orchestrator-1.0.jar

# Search Modrinth for "sodium"
echo '{"type": "modding", "modBrowser": "modrinth", "subType": "search", "modName": "sodium", "version": "1.20.4", "modLoader": "fabric", "modId": "0"}' | java -jar murces-orchestrator-1.0.jar
```

#### Python Subprocess (Bot or Web Backend)
```python
import subprocess
import json

proc = subprocess.Popen(
    ["java", "-jar", "murces-orchestrator-1.0.jar"],
    stdin=subprocess.PIPE,
    stdout=subprocess.PIPE,
    text=True,
    bufsize=1
)

def send_command(payload):
    proc.stdin.write(json.dumps(payload) + "\n")
    proc.stdin.flush()
    return json.loads(proc.stdout.readline())

# Check server status
status = send_command({
    "type": "server",
    "serverType": "none",
    "gameVersion": "none",
    "loaderVersion": "none",
    "ram": 0,
    "job": 4
})
print(f"Server running: {status.get('running')}")
```

For complete documentation on all request parameters, status codes, and response structures, see [`doc/API-SPEC.md`](doc/API-SPEC.md).

---

## Configuration

If you downloaded the precompiled release (`murces.zip`), you do **not** need to set up an API key or email. The CurseForge API key and contact details are already baked into the release binary during compilation, so mod browsing works out of the box.

Setting up a `.env` file or environment variables is only needed if you are **building from source** or want to override the defaults with your own developer credentials:

```sh
curseAPI="YOUR_KEY"
email="your_email@example.com"
```

---

## Issues & Contributing

If you encounter bugs, broken dependencies, or have suggestions, feel free to open an issue on the [GitHub Issue Tracker](https://github.com/DeployedReject/murces/issues).