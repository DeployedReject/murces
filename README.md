# MurCes

```
  __  __                                
 |  \/  |_   _ _ __ ___ ___  ___        
 | |\/| | | | | '__/ __/ _ \/ __|       
 | |  | | |_| | | | (_|  __/\__ \       
 |_|  |_|\__,_|_|  \___\___||___/       
    Minecraft Server Manager v0.1       
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