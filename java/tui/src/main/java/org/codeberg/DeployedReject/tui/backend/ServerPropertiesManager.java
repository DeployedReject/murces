package org.codeberg.DeployedReject.tui.backend;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ServerPropertiesManager {

    public static class PropertyDef {
        public enum Type { BOOLEAN, ENUM, INTEGER, STRING }

        private final String key;
        private final Type type;
        private final String defaultValue;
        private final String category;
        private final String description;
        private final List<String> options;

        public PropertyDef(String key, Type type, String defaultValue, String category, String description) {
            this(key, type, defaultValue, category, description, Collections.emptyList());
        }

        public PropertyDef(String key, Type type, String defaultValue, String category, String description, List<String> options) {
            this.key = key;
            this.type = type;
            this.defaultValue = defaultValue;
            this.category = category;
            this.description = description;
            this.options = options;
        }

        public String getKey() { return key; }
        public Type getType() { return type; }
        public String getDefaultValue() { return defaultValue; }
        public String getCategory() { return category; }
        public String getDescription() { return description; }
        public List<String> getOptions() { return options; }
    }

    private static final LinkedHashMap<String, PropertyDef> DEFINITIONS = new LinkedHashMap<>();

    private static void def(String key, PropertyDef.Type type, String defVal, String cat, String desc) {
        DEFINITIONS.put(key, new PropertyDef(key, type, defVal, cat, desc));
    }

    private static void def(String key, PropertyDef.Type type, String defVal, String cat, String desc, List<String> opts) {
        DEFINITIONS.put(key, new PropertyDef(key, type, defVal, cat, desc, opts));
    }

    static {

        def("gamemode", PropertyDef.Type.ENUM, "survival", "Gameplay", "Default game mode for new players", Arrays.asList("survival", "creative", "adventure", "spectator"));
        def("difficulty", PropertyDef.Type.ENUM, "easy", "Gameplay", "Game difficulty level", Arrays.asList("peaceful", "easy", "normal", "hard"));
        def("hardcore", PropertyDef.Type.BOOLEAN, "false", "Gameplay", "Enable hardcore mode (permanent death / spectator)");
        def("pvp", PropertyDef.Type.BOOLEAN, "true", "Gameplay", "Enable Player vs Player combat");
        def("allow-flight", PropertyDef.Type.BOOLEAN, "false", "Gameplay", "Allow flight in survival mode (mod flight)");
        def("force-gamemode", PropertyDef.Type.BOOLEAN, "false", "Gameplay", "Force default game mode upon player rejoin");
        def("spawn-monsters", PropertyDef.Type.BOOLEAN, "true", "Gameplay", "Enable hostile monster spawning");
        def("spawn-animals", PropertyDef.Type.BOOLEAN, "true", "Gameplay", "Enable friendly animal spawning");
        def("spawn-npcs", PropertyDef.Type.BOOLEAN, "true", "Gameplay", "Enable NPC and villager spawning");
        def("spawn-protection", PropertyDef.Type.INTEGER, "16", "Gameplay", "Radius in blocks of spawn protection area (0 to disable)");
        def("enable-command-block", PropertyDef.Type.BOOLEAN, "false", "Gameplay", "Allow command blocks to execute commands");
        def("player-idle-timeout", PropertyDef.Type.INTEGER, "0", "Gameplay", "Minutes idle before kicking player (0 disables)");
        def("pause-when-empty-seconds", PropertyDef.Type.INTEGER, "60", "Gameplay", "Seconds empty before server pauses ticks (-1 disables)");

        def("level-name", PropertyDef.Type.STRING, "world", "World", "World save directory and level name");
        def("level-seed", PropertyDef.Type.STRING, "", "World", "World generator seed (empty for random)");
        def("level-type", PropertyDef.Type.ENUM, "minecraft:normal", "World", "World generator preset", Arrays.asList("minecraft:normal", "minecraft:flat", "minecraft:large_biomes", "minecraft:amplified", "minecraft:single_biome_surface"));
        def("generate-structures", PropertyDef.Type.BOOLEAN, "true", "World", "Generate structures (villages, dungeons, etc.)");
        def("generator-settings", PropertyDef.Type.STRING, "{}", "World", "Custom world generator JSON settings");
        def("allow-nether", PropertyDef.Type.BOOLEAN, "true", "World", "Allow travel to the Nether dimension");
        def("max-world-size", PropertyDef.Type.INTEGER, "29999984", "World", "Maximum world border radius in blocks");

        def("server-port", PropertyDef.Type.INTEGER, "25565", "Network", "TCP port number the Minecraft server listens on");
        def("server-ip", PropertyDef.Type.STRING, "", "Network", "IP address to bind server (empty for all interfaces)");
        def("max-players", PropertyDef.Type.INTEGER, "20", "Network", "Maximum concurrent connected players");
        def("online-mode", PropertyDef.Type.BOOLEAN, "true", "Network", "Authenticate player accounts with Mojang session servers");
        def("white-list", PropertyDef.Type.BOOLEAN, "false", "Network", "Enable whitelist (only listed players can join)");
        def("enforce-whitelist", PropertyDef.Type.BOOLEAN, "false", "Network", "Kick non-whitelisted players on whitelist reload");
        def("motd", PropertyDef.Type.STRING, "A Minecraft Server", "Network", "Message of the Day shown in multiplayer server browser");
        def("hide-online-players", PropertyDef.Type.BOOLEAN, "false", "Network", "Hide player count and list from server status ping");
        def("prevent-proxy-connections", PropertyDef.Type.BOOLEAN, "false", "Network", "Kick players connecting through VPNs or proxies");
        def("network-compression-threshold", PropertyDef.Type.INTEGER, "256", "Network", "Packet byte size threshold for compression (-1 disables)");
        def("rate-limit", PropertyDef.Type.INTEGER, "0", "Network", "Max packets per second before kicking (0 disables)");
        def("accepts-transfers", PropertyDef.Type.BOOLEAN, "false", "Network", "Accept incoming transfers via transfer packet");
        def("allowed-connection-ids", PropertyDef.Type.STRING, "", "Network", "Comma-separated list of allowed connection IDs");
        def("status-contact-details", PropertyDef.Type.STRING, "", "Network", "Contact info shown in server status query");
        def("status-heartbeat-interval", PropertyDef.Type.INTEGER, "0", "Network", "Server status heartbeat interval in ticks");

        def("view-distance", PropertyDef.Type.INTEGER, "10", "Performance", "Render distance in chunks sent to clients (3-32)");
        def("simulation-distance", PropertyDef.Type.INTEGER, "10", "Performance", "Tick simulation distance in chunks (3-32)");
        def("entity-broadcast-range-percentage", PropertyDef.Type.INTEGER, "100", "Performance", "Entity rendering distance percentage (10-1000)");
        def("max-tick-time", PropertyDef.Type.INTEGER, "60000", "Performance", "Max ms per tick before watchdog terminates server (-1 disables)");
        def("max-chained-neighbor-updates", PropertyDef.Type.INTEGER, "1000000", "Performance", "Max consecutive neighbor block updates limit");
        def("sync-chunk-writes", PropertyDef.Type.BOOLEAN, "true", "Performance", "Synchronous chunk writes to disk for crash safety");
        def("use-native-transport", PropertyDef.Type.BOOLEAN, "true", "Performance", "Use native Linux epoll packet transport");
        def("region-file-compression", PropertyDef.Type.ENUM, "deflate", "Performance", "Chunk region file compression algorithm", Arrays.asList("deflate", "lz4", "none"));

        def("enforce-secure-profile", PropertyDef.Type.BOOLEAN, "true", "Security", "Enforce cryptographically signed chat messages");
        def("op-permission-level", PropertyDef.Type.ENUM, "4", "Security", "Default permission level for server operators (1-4)", Arrays.asList("1", "2", "3", "4"));
        def("function-permission-level", PropertyDef.Type.ENUM, "2", "Security", "Permission level required for datapack functions (1-4)", Arrays.asList("1", "2", "3", "4"));
        def("broadcast-console-to-ops", PropertyDef.Type.BOOLEAN, "true", "Security", "Broadcast console command outputs to online ops");
        def("broadcast-rcon-to-ops", PropertyDef.Type.BOOLEAN, "true", "Security", "Broadcast RCON outputs to online ops");
        def("log-ips", PropertyDef.Type.BOOLEAN, "true", "Security", "Log client IP addresses in server console");
        def("chat-spam-threshold-seconds", PropertyDef.Type.INTEGER, "10", "Security", "Seconds threshold before chat spam warning");
        def("command-spam-threshold-seconds", PropertyDef.Type.INTEGER, "10", "Security", "Seconds threshold before command spam warning");
        def("enable-status", PropertyDef.Type.BOOLEAN, "true", "Security", "Allow server to appear online in multiplayer list");
        def("enable-legacy-status", PropertyDef.Type.BOOLEAN, "true", "Security", "Support legacy 1.6 server status query");
        def("enable-query", PropertyDef.Type.BOOLEAN, "false", "Security", "Enable GameSpy4 query protocol");
        def("query.port", PropertyDef.Type.INTEGER, "25565", "Security", "Listening port for GameSpy4 query protocol");
        def("enable-rcon", PropertyDef.Type.BOOLEAN, "false", "Security", "Enable Remote Console (RCON) administration");
        def("rcon.port", PropertyDef.Type.INTEGER, "25575", "Security", "Listening port for RCON protocol");
        def("rcon.password", PropertyDef.Type.STRING, "", "Security", "Password for RCON remote authentication");
        def("enable-jmx-monitoring", PropertyDef.Type.BOOLEAN, "false", "Security", "Enable Java JMX performance monitoring");
        def("enable-code-of-conduct", PropertyDef.Type.BOOLEAN, "false", "Security", "Display code of conduct prompt to players");
        def("bug-report-link", PropertyDef.Type.STRING, "", "Security", "Custom bug report URL displayed in client");
        def("text-filtering-config", PropertyDef.Type.STRING, "", "Security", "Text filtering service configuration");
        def("text-filtering-version", PropertyDef.Type.INTEGER, "0", "Security", "Text filtering service version number");
        def("management-server-enabled", PropertyDef.Type.BOOLEAN, "false", "Security", "Enable management HTTP/HTTPS server");
        def("management-server-host", PropertyDef.Type.STRING, "localhost", "Security", "Host for management server");
        def("management-server-port", PropertyDef.Type.INTEGER, "0", "Security", "Port for management server");
        def("management-server-secret", PropertyDef.Type.STRING, "", "Security", "Shared secret authentication for management server");
        def("management-server-tls-enabled", PropertyDef.Type.BOOLEAN, "true", "Security", "Enable TLS encryption for management server");
        def("management-server-tls-keystore", PropertyDef.Type.STRING, "", "Security", "TLS keystore file path for management server");
        def("management-server-tls-keystore-password", PropertyDef.Type.STRING, "", "Security", "TLS keystore password for management server");

        def("require-resource-pack", PropertyDef.Type.BOOLEAN, "false", "Resource Packs", "Disconnect players who decline the resource pack");
        def("resource-pack", PropertyDef.Type.STRING, "", "Resource Packs", "Direct download URL for server resource pack");
        def("resource-pack-prompt", PropertyDef.Type.STRING, "", "Resource Packs", "Custom prompt message shown when offering resource pack");
        def("resource-pack-sha1", PropertyDef.Type.STRING, "", "Resource Packs", "SHA-1 hash checksum (40 hex chars) of resource pack");
        def("resource-pack-id", PropertyDef.Type.STRING, "", "Resource Packs", "UUID identifier for server resource pack");
        def("initial-enabled-packs", PropertyDef.Type.STRING, "vanilla", "Resource Packs", "Comma-separated list of datapacks enabled by default");
        def("initial-disabled-packs", PropertyDef.Type.STRING, "", "Resource Packs", "Comma-separated list of datapacks disabled by default");
    }

    public static final List<String> CATEGORIES = Arrays.asList(
            "All",
            "Gameplay",
            "World",
            "Network",
            "Performance",
            "Security",
            "Resource Packs",
            "Custom"
    );

    private final Path filePath;
    private final LinkedHashMap<String, String> properties = new LinkedHashMap<>();

    public ServerPropertiesManager() {
        this("server.properties");
    }

    public ServerPropertiesManager(String filename) {
        Path path = Paths.get(filename);
        if (!path.toFile().exists()) {
            Path cPath = Paths.get("c/manager/server.properties");
            if (cPath.toFile().exists()) {
                path = cPath;
            }
        }
        this.filePath = path;
        load();
    }

    public synchronized void load() {
        initDefaults();
        File file = filePath.toFile();
        if (!file.exists()) {
            return;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int eqIdx = line.indexOf('=');
                if (eqIdx > 0) {
                    String key = line.substring(0, eqIdx).trim();
                    String val = line.substring(eqIdx + 1).trim();
                    if (!key.isEmpty() && !"=(null)".equals(line)) {

                        val = val.replace("\\:", ":");
                        properties.put(key, val);
                        if (!DEFINITIONS.containsKey(key)) {
                            DEFINITIONS.put(key, new PropertyDef(key, PropertyDef.Type.STRING, val, "Custom", "Custom user or mod property"));
                        }
                    }
                }
            }
        } catch (IOException ignored) {

        }
    }

    public synchronized void initDefaults() {
        properties.clear();
        for (Map.Entry<String, PropertyDef> entry : DEFINITIONS.entrySet()) {
            properties.put(entry.getKey(), entry.getValue().getDefaultValue());
        }
    }

    public synchronized void resetDefaults() {
        initDefaults();
    }

    public synchronized void save() throws IOException {
        File file = filePath.toFile();
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            writer.write("# Minecraft server properties generated by Murces TUI\n");
            writer.write("# Complete configuration containing all " + properties.size() + " properties\n\n");

            for (String cat : CATEGORIES) {
                if ("All".equals(cat)) continue;
                List<String> catKeys = getKeysForCategory(cat);
                if (catKeys.isEmpty()) continue;

                writer.write("# ------------------------------------------------------------\n");
                writer.write("# " + cat.toUpperCase() + " PROPERTIES\n");
                writer.write("# ------------------------------------------------------------\n");
                for (String k : catKeys) {
                    String v = properties.getOrDefault(k, "");

                    if ("level-type".equals(k) && v.contains(":")) {
                        v = v.replace(":", "\\:");
                    }
                    writer.write(k + "=" + v + "\n");
                }
                writer.write("\n");
            }
        }
    }

    public synchronized String get(String key, String defaultValue) {
        return properties.getOrDefault(key, defaultValue);
    }

    public synchronized void set(String key, String value) {
        properties.put(key, value);
        if (!DEFINITIONS.containsKey(key)) {
            DEFINITIONS.put(key, new PropertyDef(key, PropertyDef.Type.STRING, value, "Custom", "Custom user or mod property"));
        }
    }

    public synchronized PropertyDef getDef(String key) {
        return DEFINITIONS.get(key);
    }

    public synchronized Map<String, PropertyDef> getAllDefinitions() {
        return new LinkedHashMap<>(DEFINITIONS);
    }

    public synchronized Map<String, String> getAll() {
        return new LinkedHashMap<>(properties);
    }

    public synchronized List<String> getAllKeys() {
        return new ArrayList<>(properties.keySet());
    }

    public synchronized List<String> getKeysForCategory(String category) {
        List<String> list = new ArrayList<>();
        if ("All".equalsIgnoreCase(category)) {
            return getAllKeys();
        }
        for (String k : properties.keySet()) {
            PropertyDef def = DEFINITIONS.get(k);
            if (def != null && def.getCategory().equalsIgnoreCase(category)) {
                list.add(k);
            } else if (def == null && "Custom".equalsIgnoreCase(category)) {
                list.add(k);
            }
        }
        return list;
    }
}
