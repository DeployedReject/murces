# API SPEC

## General Introduction

This application uses a standard JSON-based IPC (Inter-Process Communication) API to communicate with the frontend UI process through standard I/O (stdin/stdout). Each line of standard input is parsed as a distinct JSON object. We will discuss each parameter it can accept and provide contextual examples for each module.

---

## Input Body Parameters

Every request sent to the backend **must** contain a `type` parameter. This represents the master category the UI wants to act upon.

Valid `type` values:

- `"kill"`: Ends the background service gracefully and terminates the Java process.
- `"server"`: Manages server instance installation, lifecycle (start/stop), commands, and deployment.
- `"modding"`: Manages mod querying and downloading.
- `"backup"`: Flushes memory, creates `.tar` world snapshots, manages retention rotation, and cloud syncs.
- `"tunnel"`: Manages native Playit.gg tunnel claim lifecycle, agent identity, and route discovery.

---

## Type: `tunnel`

Manages Playit.gg agent claim handshakes, route queries, and credential resets.

**Parameters:**
- `action`: The tunnel operation to perform:
  - `"setup"`: Initiates claim setup, prints claim verification URL (`https://playit.gg/claim/<code>`), and begins background exchange polling.
  - `"status"`: Queries agent rundata via `GET /agents/rundata` to inspect assigned routes, public domains, and local ports.
  - `"reset"`: Removes local credentials (`playitagent.txt`) and cache (`tunnels.json`).

**Example (Setup Tunnel):**
`{"type": "tunnel", "action": "setup"}`

**Example (Query Status):**
`{"type": "tunnel", "action": "status"}`

**Example (Reset Credentials):**
`{"type": "tunnel", "action": "reset"}`

---

## Type: `backup`

Triggers live memory flushing (`save-all` & `save-off`), creates a timestamped `.tar` snapshot of the target world, cleans up old snapshots beyond the retention quota, re-enables saving (`save-on`), and optionally synchronizes to Google Drive via `rclone`.

**Parameters:**
- `sourceFolder`: Source world folder (e.g., `"world"`, optional, default `"world"`).
- `targetFolder`: Directory for archives (e.g., `"backup"`, optional, default `"backup"`).
- `retentionLimit`: Number of newest backups to retain (e.g., `3`, optional, default `3`).
- `cloudSync`: Boolean toggle to sync via rclone (`true` / `false`, default `false`).
- `cloudRemote`: Rclone remote name (e.g., `"minecraftdrive"`, default `"minecraftdrive"`).
- `sessionName`: Target tmux session name (e.g., `"mcsv"`, optional, default `"mcsv"`).
- `serverDir`: Target server root directory (e.g., `"."` or `"servers/survival"`, optional, default `"."`).

**Example:**
`{"type": "backup", "sourceFolder": "world", "targetFolder": "backup", "retentionLimit": 3, "cloudSync": false, "cloudRemote": "minecraftdrive", "sessionName": "mcsv", "serverDir": "."}`

---

## Type: `server`

The server module manages the download, installation, and background process execution (`tmux`) of Minecraft servers. It automatically bypasses the EULA.

**Required Parameters:**
`type`, `gameVersion`, `loaderVersion`, `serverType`, `ram`, `job`

**Optional Parameters:**
- `sessionName`: Name of the background tmux session (default: `"mcsv"`).
- `serverDir`: Working directory for the server instance (default: `"."`).
- `command`: In-game console command for `job: 7`.
- `oldName` / `newName`: Old and new account usernames for player UUID migration (`job: 8`).
- `portableJdk`: Boolean toggle to use portable Adoptium OpenJDK.

- `serverType`: The backend engine ("fabric", "spigot", "paper", "forge", "vanilla").
- `gameVersion`: The Minecraft version (e.g., "1.20.4").
- `loaderVersion`: The specific build of the server type (e.g., "0.15.7"). Used primarily for Fabric. If using other engines, pass "none".
- `ram`: Total RAM in Gigabytes to allocate to the server JVM (e.g., `4`).
- `job`: An integer defining the lifecycle action.
  - `0` : **Install Only** (Downloads/compiles the server but does not start it).
  - `1` : **Install & Start** (Downloads/compiles and immediately spawns the `tmux` session).
  - `2` : **Stop** (Gracefully stops the active Minecraft server tmux session).
  - `3` : **Check Supported Engines** (Returns a list of currently implemented server types).
  - `4` : **Check Status** (Returns whether the server tmux session is currently active).
  - `5` : **Start Only** (Spawns the `tmux` session for an already installed server without reinstalling).
  - `6` : **Restart** (Stops the active server session, waits briefly, and launches it again).
  - `7` : **Console Command** (Dispatches in-game command specified in `"command"` parameter).
  - `8` : **Migrate Player** (Migrates player data specified in `"oldName"` and `"newName"`).
  - `9` : **Backup** (Runs world snapshot using configured backup parameters).

**Example (Install & Launch Paper):**
`{"type": "server", "serverType": "paper", "gameVersion": "1.20.4", "loaderVersion": "none", "ram": 4, "job": 1}`

**Example (Stop Server):**
`{"type": "server", "serverType": "none", "gameVersion": "none", "loaderVersion": "none", "ram": 0, "job": 2}`

**Example (Dispatch Console Command):**
`{"type": "server", "serverType": "none", "gameVersion": "none", "loaderVersion": "none", "ram": 0, "job": 7, "command": "say Hello World"}`

**Example (Migrate Player UUID):**
`{"type": "server", "serverType": "none", "gameVersion": "none", "loaderVersion": "none", "ram": 0, "job": 8, "oldName": "OldNick", "newName": "NewNick"}`

---

## Responses

The backend communicates back to the UI via stdout using standardized JSON objects. The frontend UI should parse standard input and primarily read the `status` key to determine state.

### Valid `status` Codes

- `0` : **Success / Continuous Update.** Used for returning search queries, acknowledging a server stop, generic check queries, or updating progress bars.
- `1` : **Fatal Error.** The job failed and was aborted.
- `2` : **Long Job Started.** Used to tell the UI to render a loading animation.
- `3` : **Long Job Finished.** Used to tell the UI to clear the loading animation.

### Response Data Structures

**1. Errors (`status: 1`)**
Always contains an `error` key with a string explaining the failure.
_Example:_ `{"status": 1, "error": "Build failed"}` or `{"status": 1, "error": "Author does not permit API downloads."}`

**2. Query Results (`type: "query"`)**
Returned when `subType` is `search` or `home`. Contains a `mods` key, which holds a 2D array of strings formatted as `[id/slug, Display Name]`.
_Example:_ `{"status": 0, "type": "query", "mods": [["roughly-enough-items", "Roughly Enough Items (REI)"], ["sodium", "Sodium"]]}`

**3. Download Progress (`type: "download"`)**
Emits a rapid stream of JSON objects. The frontend should update its progress bar using the `progress` float (0 to 100). Sometimes it will be negative; that means a valid `content-length` header was missing and a definitive progress bar is not possible.
_Flow:_

1. `{"status": 2, "type": "download", "progress": 0}`
2. `{"status": 0, "type": "download", "progress": 14.5}`
3. `{"status": 0, "type": "download", "progress": 89.2}`
4. `{"status": 3, "type": "download", "progress": 100.0}`

**4. Server Deployment (`type: "server"`)**
Servers take time to download (Paper, Fabric, Forge, Vanilla), compile (Spigot), and launch (`tmux`). They utilize the start/stop status codes.

_Flow (Install Only - `job: 0`):_
1. `{"status": 2, "type": "server"}` _(Downloading/compiling assets)_
2. `{"status": 3, "type": "server"}` _(Server installation finished)_

_Flow (Install & Launch - `job: 1`):_
1. `{"status": 2, "type": "server"}` _(Downloading/compiling assets or executing tmux command)_
2. `{"status": 3, "type": "server"}` _(Server successfully handed off to background tmux session)_

_Flow (Stop Server - `job: 2`):_
1. `{"status": 0, "type": "server"}` _(Emitted immediately upon successfully killing the tmux session)_

_Flow (Check Server Status - `job: 4`):_
1. `{"status": 0, "type": "server", "running": true}` _(or `false` if no active session)_

_Flow (Start Only - `job: 5` or Restart - `job: 6`):_
1. `{"status": 2, "type": "server"}` _(Initiating server launch)_
2. `{"status": 3, "type": "server"}` _(Server successfully running in tmux)_

**5. Supported Engine List (`job: 3`)**
Returned when the frontend asks for supported loaders. Includes `serverList` and `server` arrays alongside the `status: 0` confirmation.
_Example:_ `{"serverList": ["fabric", "spigot", "paper", "vanilla", "forge"], "server": ["fabric", "spigot", "paper", "vanilla", "forge"], "status": 0, "type": "server"}`
