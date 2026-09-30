# MurCes

```
  __  __                                
 |  \/  |_   _ _ __ ___ ___  ___        
 | |\/| | | | | '__/ __/ _ \/ __|       
 | |  | | |_| | | | (_|  __/\__ \       
 |_|  |_|\__,_|_|  \___\___||___/       
    Minecraft Server Manager v0.1       
```

**MurCes** is a lightweight, all-in-one Minecraft server manager designed for Linux environments. It provides both an interactive Terminal User Interface (TUI) and headless CLI commands to configure, deploy, mod, and maintain Minecraft servers effortlessly—without requiring complex terminal scripts or heavy web panel stacks.

MurCes is compiled ahead-of-time (AOT) into a standalone native binary via GraalVM, making it start instantly with minimal memory usage, optimized for low-resource VPS instances and older laptops.

---

## 📌 Repository Branches

- **`unstable` (Default / Main Branch)**: The active development branch containing the full Lanterna-based TUI, integrated orchestrator backend, and native GraalVM build configuration.
- **`archived`**: Preserves the legacy C prototype and precursor-based TUI implementation for historical reference.

---

## ✨ Features

- **🚀 Engine Installer**: Download, install, and configure **Paper**, **Fabric**, **Forge**, **Spigot**, and **Vanilla** servers across any Minecraft version. Automatically agrees to the Mojang EULA.
- **📦 Mod Browser & Downloader**: Direct integration with both **Modrinth** and **CurseForge** APIs. Search, filter by loader (Fabric, Forge, NeoForge, Quilt) and game version, and download mods straight to `mods/`.
- **🎮 Server Lifecycle & Console**: Runs servers in detached `tmux` sessions. Send console commands directly from the TUI with live log capture.
- **🌐 Public Tunneling**: Seamless integration with [Playit.gg](https://playit.gg/) (`--public` / `-p`) to make your server publicly accessible without port forwarding.
- **⚙️ Server Properties Editor**: Real-time property searching and categorized editor for all `server.properties` settings.
- **💾 World Backups**: Automated tar backups with safe world save flushing (`save-all`, `save-off`, `save-on`), backup retention rotation, and optional cloud sync via `rclone`.
- **🔄 Player UUID Migration**: Effortlessly migrate player data (`playerdata`, `stats`, `advancements`, `usercache.json`) between offline and online UUIDs.

---

## 🚀 Quickstart

### Running the Native Binary
If you have the compiled `murces` binary:
```sh
chmod +x murces
./murces
```

### CLI Commands
In addition to the interactive TUI, MurCes supports headless CLI subcommands:

```sh
murces                  # Launch interactive TUI
murces start [-p]       # Start server (add -p / --public for Playit.gg tunnel)
murces stop             # Stop running server
murces status           # Query whether server is running
murces backup           # Trigger a world backup
murces --test-tui       # Run automated self-test across all 7 TUI modules
murces --help           # Show help and usage options
murces --version        # Display version information
```

---

## 🛠️ Building from Source

### Prerequisites
- **Linux** (or WSL on Windows 10+)
- **JDK 21+** (JDK 25 recommended)
- **Apache Maven 3.8+**
- **tmux** (required for background server execution)
- **GraalVM Native Image** (optional, only required if compiling a standalone native binary)

### 1. Build the Orchestrator
```sh
cd java/orchestrator
mvn clean install
cd ../..
```

### 2. Build the TUI (Jar)
```sh
cd java/tui
mvn clean package
# The shaded JAR will be at java/tui/target/murces-tui-0.1.jar
```

### 3. Build Native Binary (GraalVM)
To compile the standalone AOT native binary (`murces`):
```sh
cd java/tui
mvn clean package -Pnative
cp target/murces ../../
```

---

## ⚙️ Configuration

Optional configuration for external services (e.g. CurseForge API key and contact email) can be supplied via environment variables or a `.env` file in the root directory:

```sh
curseAPI="YOUR_CURSEFORGE_API_KEY"
email="your_email@example.com"
```

---

## 🤝 Contributing & Issues

Feedback, feature requests, and bug reports are welcome! Please open an issue on the [GitHub Issue Tracker](https://github.com/DeployedReject/murces/issues).