package org.codeberg.DeployedReject.tui.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public class ConfigManager {

    private static final String CONFIG_FILENAME = "murces.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static ConfigManager instance;

    private TuiConfig config;
    private final File configFile;

    private ConfigManager() {
        this.configFile = new File(CONFIG_FILENAME);
        load();
    }

    public static synchronized ConfigManager getInstance() {
        if (instance == null) {
            instance = new ConfigManager();
        }
        return instance;
    }

    public synchronized TuiConfig getConfig() {
        if (config == null) {
            config = new TuiConfig();
        }
        return config;
    }

    public synchronized void setConfig(TuiConfig newConfig) {
        if (newConfig != null) {
            this.config = newConfig;
            save();
        }
    }

    public synchronized void saveConfig() {
        save();
    }

    public synchronized void load() {
        if (configFile.exists() && configFile.isFile()) {
            try (FileReader reader = new FileReader(configFile, StandardCharsets.UTF_8)) {
                JsonObject obj = JsonParser.parseReader(reader).getAsJsonObject();
                TuiConfig loaded = new TuiConfig();
                if (obj.has("theme") && !obj.get("theme").isJsonNull()) {
                    String th = obj.get("theme").getAsString().trim();
                    if (!th.isEmpty()) {
                        loaded.setTheme(th);
                    }
                }
                if (obj.has("transparencyPercent")) {
                    loaded.setTransparencyPercent(obj.get("transparencyPercent").getAsInt());
                }
                if (obj.has("trueColor")) {
                    loaded.setTrueColor(obj.get("trueColor").getAsBoolean());
                }
                if (obj.has("enforceMinSize")) {
                    loaded.setEnforceMinSize(obj.get("enforceMinSize").getAsBoolean());
                }
                if (obj.has("pickaxeAnimation")) {
                    loaded.setPickaxeAnimation(obj.get("pickaxeAnimation").getAsBoolean());
                }
                if (obj.has("nerdFontMode") && !obj.get("nerdFontMode").isJsonNull()) {
                    loaded.setNerdFontMode(obj.get("nerdFontMode").getAsString().trim());
                }
                if (obj.has("sessionName") && !obj.get("sessionName").isJsonNull()) {
                    loaded.setSessionName(obj.get("sessionName").getAsString().trim());
                }
                if (obj.has("serverDir") && !obj.get("serverDir").isJsonNull()) {
                    loaded.setServerDir(obj.get("serverDir").getAsString().trim());
                }
                if (obj.has("activeServer") && !obj.get("activeServer").isJsonNull()) {
                    loaded.setActiveServer(obj.get("activeServer").getAsString().trim());
                }
                if (obj.has("serverProfiles") && obj.get("serverProfiles").isJsonObject()) {
                    JsonObject profObj = obj.getAsJsonObject("serverProfiles");
                    Map<String, TuiConfig.ServerProfile> profiles = new LinkedHashMap<>();
                    for (Map.Entry<String, JsonElement> entry : profObj.entrySet()) {
                        if (entry.getValue().isJsonObject()) {
                            TuiConfig.ServerProfile sp = GSON.fromJson(entry.getValue(), TuiConfig.ServerProfile.class);
                            profiles.put(entry.getKey(), sp);
                        }
                    }
                    if (!profiles.isEmpty()) {
                        loaded.setServerProfiles(profiles);
                    }
                }
                if (obj.has("portableJdk")) {
                    loaded.setPortableJdk(obj.get("portableJdk").getAsBoolean());
                }
                if (obj.has("gameVersion") && !obj.get("gameVersion").isJsonNull()) {
                    loaded.setGameVersion(obj.get("gameVersion").getAsString().trim());
                }
                if (obj.has("loader") && !obj.get("loader").isJsonNull()) {
                    loaded.setLoader(obj.get("loader").getAsString().trim());
                }
                if (obj.has("backupSourceFolder") && !obj.get("backupSourceFolder").isJsonNull()) {
                    loaded.setBackupSourceFolder(obj.get("backupSourceFolder").getAsString().trim());
                }
                if (obj.has("backupTargetFolder") && !obj.get("backupTargetFolder").isJsonNull()) {
                    loaded.setBackupTargetFolder(obj.get("backupTargetFolder").getAsString().trim());
                }
                if (obj.has("backupRetentionLimit")) {
                    loaded.setBackupRetentionLimit(obj.get("backupRetentionLimit").getAsInt());
                }
                if (obj.has("backupCloudSync")) {
                    loaded.setBackupCloudSync(obj.get("backupCloudSync").getAsBoolean());
                }
                if (obj.has("backupCloudRemote") && !obj.get("backupCloudRemote").isJsonNull()) {
                    loaded.setBackupCloudRemote(obj.get("backupCloudRemote").getAsString().trim());
                }
                this.config = loaded;
                return;
            } catch (Exception ignored) {}
        }

        this.config = new TuiConfig();
        save();
    }

    public synchronized void save() {
        if (config == null) return;
        try (FileWriter writer = new FileWriter(configFile, StandardCharsets.UTF_8)) {
            JsonObject obj = new JsonObject();
            obj.addProperty("theme", config.getTheme());
            obj.addProperty("transparencyPercent", config.getTransparencyPercent());
            obj.addProperty("trueColor", config.isTrueColor());
            obj.addProperty("enforceMinSize", config.isEnforceMinSize());
            obj.addProperty("pickaxeAnimation", config.isPickaxeAnimation());
            obj.addProperty("nerdFontMode", config.getNerdFontMode());
            obj.addProperty("sessionName", config.getSessionName());
            obj.addProperty("serverDir", config.getServerDir());
            obj.addProperty("activeServer", config.getActiveServer());
            obj.add("serverProfiles", GSON.toJsonTree(config.getServerProfiles()));
            obj.addProperty("portableJdk", config.isPortableJdk());
            obj.addProperty("gameVersion", config.getGameVersion());
            obj.addProperty("loader", config.getLoader());
            obj.addProperty("backupSourceFolder", config.getBackupSourceFolder());
            obj.addProperty("backupTargetFolder", config.getBackupTargetFolder());
            obj.addProperty("backupRetentionLimit", config.getBackupRetentionLimit());
            obj.addProperty("backupCloudSync", config.isBackupCloudSync());
            obj.addProperty("backupCloudRemote", config.getBackupCloudRemote());
            GSON.toJson(obj, writer);
        } catch (Exception ignored) {}
    }
}
