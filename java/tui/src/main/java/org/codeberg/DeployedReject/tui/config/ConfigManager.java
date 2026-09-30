package org.codeberg.DeployedReject.tui.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;

/**
 * Handles automatic loading, saving, and default generation for murces.json.
 */
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
                this.config = loaded;
                return;
            } catch (Exception ignored) {}
        }

        // Generate default config file if missing or failed to parse
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
            GSON.toJson(obj, writer);
        } catch (Exception ignored) {}
    }
}
