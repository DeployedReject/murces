# API SPEC

## General Introduction

This application uses a standard JSON-based IPC (Inter-Process Communication) API to communicate with the frontend UI process through standard I/O (stdin/stdout). Each line of standard input is parsed as a distinct JSON object. We will discuss each parameter it can accept and provide contextual examples for each module.

---

## Input Body Parameters

Every request sent to the backend **must** contain a `type` parameter. This represents the master category the UI wants to act upon.

Valid `type` values:

- `"kill"`: Ends the background service gracefully and terminates the Java process.
- `"server"`: Manages server instance installation and deployment.
- `"modding"`: Manages mod querying and downloading.

_(Note: If a required parameter for a specific type is missing, the backend will immediately return a `status: 1` error.)_

---

## Type: `kill`

Gracefully breaks the listener loop and shuts down the Java thread pool.
**Example:** `{"type": "kill"}`

---

## Type: `modding`

The modding module acts as a unified translation layer between the UI and multiple external mod repositories.

**Required Parameters for ALL modding requests:**
`type`, `modBrowser`, `subType`, `modName`, `version`, `modLoader`, `modId`

_(Note: Even if a specific `subType` doesn't use all parameters—e.g., searches don't need a `modId`—they must still be included in the JSON with dummy data like `"0"` or `"none"` to pass the backend safety checks)._

### `modBrowser`: "modrinth" | "curseForge"

Defines which external API the backend should route the request to.

### `subType`: "search" | "home" | "download"

This represents the specific action to take within the selected mod browser.

#### 1. `search`

Searches the database based on the provided parameters.

- `modName`: The search term (e.g., "lithium").
- `version`: The required Minecraft game version (e.g., "1.20.4").
- `modLoader`: The target loader (e.g., "fabric", "forge", "neoForge", "quilt", "liteLoader", "cauldron").

**Example (Modrinth Search):**
`{"type": "modding", "modBrowser": "modrinth", "subType": "search", "modName": "lithium", "version": "1.20.4", "modLoader": "fabric", "modId": "0"}`

**Example (CurseForge Search):**
`{"type": "modding", "modBrowser": "curseForge", "subType": "search", "modName": "jei", "version": "1.20.4", "modLoader": "fabric", "modId": "0"}`

#### 2. `home`

Fetches a generic list of featured/top mods to populate the UI if the user hasn't typed a query yet. You still need to pass dummy data for `modName` to satisfy the master check.

**Example:**
`{"type": "modding", "modBrowser": "modrinth", "subType": "home", "modName": "none", "version": "1.20.4", "modLoader": "fabric", "modId": "0"}`

#### 3. `download`

Downloads the exact mod matching the query and places it in the `mods/` directory.

- For **Modrinth**: `modName` must be the exact internal `slug` of the mod.
- For **CurseForge**: `modId` must be the exact numeric ID of the mod.

**Example (Modrinth Download):**
`{"type": "modding", "modBrowser": "modrinth", "subType": "download", "modName": "roughly-enough-items", "version": "1.20.4", "modLoader": "fabric", "modId": "0"}`

**Example (CurseForge Download):**
`{"type": "modding", "modBrowser": "curseForge", "subType": "download", "modName": "none", "version": "1.20.4", "modLoader": "fabric", "modId": "238222"}`

---

## Type: `server`

The server module downloads the required server executable, automatically bypasses the EULA, and launches the server in a detached OS background process (`tmux`).

**Required Parameters:**
`type`, `gameVersion`, `loaderVersion`, `serverType`, `ram`

- `serverType`: The backend engine ("fabric", "spigot", "vanilla"). _(forge and paper are placeholders)_
- `gameVersion`: The Minecraft version (e.g., "1.20.4").
- `loaderVersion`: The specific build of the server type (e.g., "0.15.7"). If using vanilla or spigot, put "none" here.
- `ram`: Total RAM in Gigabytes to allocate to the server JVM (e.g., `4`).

**Example (Fabric Launch):**
`{"type": "server", "serverType": "fabric", "gameVersion": "1.20.4", "loaderVersion": "0.15.7", "ram": 4}`

**Example (Spigot Compile & Launch):**
`{"type": "server", "serverType": "spigot", "gameVersion": "1.20.4", "loaderVersion": "none", "ram": 4}`

---

## Output Schema (Responses)

The backend communicates back to the UI via stdout using standardized JSON objects. The frontend UI should parse standard input and primarily read the `status` key to determine state.

### The `status` Code Protocol

- `0` : **Success / Continuous Update.** Used for returning search queries or updating progress bars.
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
Emits a rapid stream of JSON objects. The frontend should update its progress bar using the `progress` float (0.0 to 100.0).
_Flow:_

1. `{"status": 2, "type": "download", "progress": 0}` _(Start)_
2. `{"status": 0, "type": "download", "progress": 14.5}` _(Updating...)_
3. `{"status": 0, "type": "download", "progress": 89.2}` _(Updating...)_
4. `{"status": 3, "type": "download", "progress": 100.0}` _(Finished)_

**4. Server Deployment (`type: "server"`)**
Servers take time to compile (Spigot) or launch (`tmux`). They utilize the start/stop status codes.
_Flow (Vanilla/Fabric):_

1. `{"status": 2}` _(Executing tmux command)_
2. `{"status": 3, "type": "server"}` _(Server successfully handed off to background session)_

_Flow (Spigot):_

1. `{"status": 2}` _(Spigot BuildTools compilation started. Expect a 5-10 minute wait)_
2. `{"status": 3}` _(BuildTools compiled successfully)_
3. `{"status": 2}` _(Executing tmux command)_
4. `{"status": 3, "type": "server"}` _(Server successfully handed off to background session)_
