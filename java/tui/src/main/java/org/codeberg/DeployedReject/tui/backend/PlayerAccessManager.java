package org.codeberg.DeployedReject.tui.backend;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.codeberg.DeployedReject.tui.config.ConfigManager;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Manages Minecraft server whitelist (whitelist.json) and blacklist (banned-players.json).
 * Handles reading, writing, adding, removing player names/UUIDs, and toggling whitelist in server.properties.
 */
public class PlayerAccessManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static class Entry {
        public String name;
        public String uuid;
        public String reason; // only for bans

        public Entry(String name, String uuid) {
            this.name = name != null ? name : "";
            this.uuid = uuid != null ? uuid : "";
            this.reason = "";
        }

        public Entry(String name, String uuid, String reason) {
            this.name = name != null ? name : "";
            this.uuid = uuid != null ? uuid : "";
            this.reason = reason != null ? reason : "";
        }

        @Override
        public String toString() {
            String uStr = uuid.isEmpty() ? "" : " (" + uuid + ")";
            String rStr = reason.isEmpty() ? "" : " - " + reason;
            return (name.isEmpty() ? uuid : name) + uStr + rStr;
        }
    }

    private static File getServerDirectory() {
        String sDir = ConfigManager.getInstance().getConfig().getServerDir();
        if (sDir != null && !sDir.trim().isEmpty()) {
            return new File(sDir.trim());
        }
        return new File(".");
    }

    // ==========================================
    // Whitelist State in server.properties
    // ==========================================
    public static boolean isWhitelistEnabled() {
        try {
            ServerPropertiesManager spm = new ServerPropertiesManager(getServerDirectory().getAbsolutePath());
            return "true".equalsIgnoreCase(spm.get("white-list", "false"));
        } catch (Exception e) {
            return false;
        }
    }

    public static void setWhitelistEnabled(boolean enabled) throws IOException {
        ServerPropertiesManager spm = new ServerPropertiesManager(getServerDirectory().getAbsolutePath());
        spm.set("white-list", String.valueOf(enabled));
        spm.save();
        // If server is currently running in tmux, send command
        dispatchConsoleCommand("whitelist " + (enabled ? "on" : "off"));
        dispatchConsoleCommand("whitelist reload");
    }

    public static void dispatchConsoleCommand(String cmd) {
        try {
            String sess = ConfigManager.getInstance().getConfig().getSessionName();
            if (sess == null || sess.isEmpty()) sess = "mcsv";
            org.codeberg.DeployedReject.device.ServerHandler.sendConsoleCommand(sess, cmd);
        } catch (Exception ignored) {}
    }

    // ==========================================
    // Whitelist Entries (whitelist.json)
    // ==========================================
    public static File getWhitelistFile() {
        return new File(getServerDirectory(), "whitelist.json");
    }

    public static List<Entry> getWhitelist() {
        List<Entry> list = new ArrayList<>();
        File file = getWhitelistFile();
        if (!file.exists()) return list;

        try (FileReader reader = new FileReader(file, StandardCharsets.UTF_8)) {
            JsonElement el = JsonParser.parseReader(reader);
            if (el.isJsonArray()) {
                for (JsonElement item : el.getAsJsonArray()) {
                    if (item.isJsonObject()) {
                        JsonObject obj = item.getAsJsonObject();
                        String name = obj.has("name") && !obj.get("name").isJsonNull() ? obj.get("name").getAsString() : "";
                        String uuid = obj.has("uuid") && !obj.get("uuid").isJsonNull() ? obj.get("uuid").getAsString() : "";
                        list.add(new Entry(name, uuid));
                    }
                }
            }
        } catch (Exception ignored) {}
        return list;
    }

    public static void addWhitelist(String nameOrUuid) throws IOException {
        if (nameOrUuid == null || nameOrUuid.trim().isEmpty()) return;
        nameOrUuid = nameOrUuid.trim();

        List<Entry> list = getWhitelist();
        boolean online = "true".equalsIgnoreCase(new ServerPropertiesManager(
            getServerDirectory().getAbsolutePath()).get("online-mode", "true"));
        Entry player = PlayerIdentityResolver.resolve(getServerDirectory(), nameOrUuid, online);

        // Re-adding a name repairs entries created with the wrong authentication mode.
        list.removeIf(e -> e.name.equalsIgnoreCase(player.name) || e.uuid.equalsIgnoreCase(player.uuid));
        list.add(player);
        saveWhitelist(list);
        // Reload the resolved UUID directly. 'whitelist add' can resolve a different
        // identity (especially on offline servers) and overwrite the file.
        dispatchConsoleCommand("whitelist reload");
    }

    public static void removeWhitelist(String nameOrUuid) throws IOException {
        if (nameOrUuid == null || nameOrUuid.trim().isEmpty()) return;
        final String target = nameOrUuid.trim();

        List<Entry> list = getWhitelist();
        boolean removed = list.removeIf(e -> e.name.equalsIgnoreCase(target) || e.uuid.equalsIgnoreCase(target));

        if (removed) {
            saveWhitelist(list);
            dispatchConsoleCommand("whitelist remove " + target);
            dispatchConsoleCommand("whitelist reload");
        }
    }

    private static void saveWhitelist(List<Entry> list) throws IOException {
        File file = getWhitelistFile();
        if (file.getParentFile() != null) file.getParentFile().mkdirs();

        JsonArray arr = new JsonArray();
        for (Entry e : list) {
            JsonObject obj = new JsonObject();
            obj.addProperty("uuid", e.uuid);
            obj.addProperty("name", e.name);
            arr.add(obj);
        }

        try (FileWriter writer = new FileWriter(file, StandardCharsets.UTF_8)) {
            GSON.toJson(arr, writer);
        }
    }

    // ==========================================
    // Blacklist / Ban Entries (banned-players.json)
    // ==========================================
    public static File getBannedPlayersFile() {
        return new File(getServerDirectory(), "banned-players.json");
    }

    public static List<Entry> getBlacklist() {
        List<Entry> list = new ArrayList<>();
        File file = getBannedPlayersFile();
        if (!file.exists()) return list;

        try (FileReader reader = new FileReader(file, StandardCharsets.UTF_8)) {
            JsonElement el = JsonParser.parseReader(reader);
            if (el.isJsonArray()) {
                for (JsonElement item : el.getAsJsonArray()) {
                    if (item.isJsonObject()) {
                        JsonObject obj = item.getAsJsonObject();
                        String name = obj.has("name") && !obj.get("name").isJsonNull() ? obj.get("name").getAsString() : "";
                        String uuid = obj.has("uuid") && !obj.get("uuid").isJsonNull() ? obj.get("uuid").getAsString() : "";
                        String reason = obj.has("reason") && !obj.get("reason").isJsonNull() ? obj.get("reason").getAsString() : "Banned by operator";
                        list.add(new Entry(name, uuid, reason));
                    }
                }
            }
        } catch (Exception ignored) {}
        return list;
    }

    public static void addBlacklist(String nameOrUuid, String reason) throws IOException {
        if (nameOrUuid == null || nameOrUuid.trim().isEmpty()) return;
        nameOrUuid = nameOrUuid.trim();
        if (reason == null || reason.trim().isEmpty()) reason = "Banned by an operator";

        List<Entry> list = getBlacklist();
        String name = "";
        String uuid = "";

        if (isUuid(nameOrUuid)) {
            uuid = nameOrUuid;
            name = nameOrUuid;
        } else {
            name = nameOrUuid;
            uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8)).toString();
        }

        for (Entry e : list) {
            if (e.name.equalsIgnoreCase(name) || (!uuid.isEmpty() && e.uuid.equalsIgnoreCase(uuid))) {
                return;
            }
        }

        list.add(new Entry(name, uuid, reason));
        saveBlacklist(list);
        dispatchConsoleCommand("ban " + name + " " + reason);
    }

    public static void removeBlacklist(String nameOrUuid) throws IOException {
        if (nameOrUuid == null || nameOrUuid.trim().isEmpty()) return;
        final String target = nameOrUuid.trim();

        List<Entry> list = getBlacklist();
        boolean removed = list.removeIf(e -> e.name.equalsIgnoreCase(target) || e.uuid.equalsIgnoreCase(target));

        if (removed) {
            saveBlacklist(list);
            dispatchConsoleCommand("pardon " + target);
        }
    }

    private static void saveBlacklist(List<Entry> list) throws IOException {
        File file = getBannedPlayersFile();
        if (file.getParentFile() != null) file.getParentFile().mkdirs();

        JsonArray arr = new JsonArray();
        for (Entry e : list) {
            JsonObject obj = new JsonObject();
            obj.addProperty("uuid", e.uuid);
            obj.addProperty("name", e.name);
            obj.addProperty("created", "2026-01-01 00:00:00 +0000");
            obj.addProperty("source", "Murces TUI");
            obj.addProperty("expires", "forever");
            obj.addProperty("reason", e.reason);
            arr.add(obj);
        }

        try (FileWriter writer = new FileWriter(file, StandardCharsets.UTF_8)) {
            GSON.toJson(arr, writer);
        }
    }

    private static boolean isUuid(String str) {
        if (str == null) return false;
        return str.matches("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
            || str.matches("^[0-9a-fA-F]{32}$");
    }
}
