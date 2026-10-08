package org.codeberg.DeployedReject.device;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.codeberg.DeployedReject.utils.ProcessResult;

import java.io.File;
import java.io.FileReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MigrationHandler {

  public static ProcessResult migratePlayer(String oldName, String newName) {
    return migratePlayer(oldName, newName, "mcsv", ".");
  }

  public static ProcessResult migratePlayer(String oldName, String newName, String sessionName, String serverDir) {
    if (oldName == null || oldName.trim().isEmpty() || newName == null || newName.trim().isEmpty()) {
      return new ProcessResult(1, "Missing arguments.\nUsage: migrate <old-name> <new-name>");
    }
    oldName = oldName.trim();
    newName = newName.trim();
    String sess = (sessionName != null && !sessionName.trim().isEmpty()) ? sessionName.trim() : "mcsv";
    File workDir = (serverDir != null && !serverDir.trim().isEmpty()) ? new File(serverDir) : new File(".");

    String worldName = "world";
    File worldDir = new File(workDir, worldName);
    File playerdataDir = new File(worldDir, "playerdata");
    File statsDir = new File(worldDir, "stats");
    File advancementsDir = new File(worldDir, "advancements");
    File usercacheFile = new File(workDir, "usercache.json");

    if (!usercacheFile.exists()) {
      return new ProcessResult(1, "usercache.json not found in server root.");
    }

    boolean wasRunning = ServerHandler.isServerRunning(sess);
    if (wasRunning) {
      ServerHandler.stopServer(sess);
      try {
        Thread.sleep(1000);
      } catch (InterruptedException ignored) {
      }
    }

    long epoch = System.currentTimeMillis() / 1000;
    String bakName = "migration_bak_" + epoch + ".tar";
    List<String> tarArgs = new ArrayList<>(Arrays.asList("tar", "-cf", bakName));
    if (playerdataDir.exists())
      tarArgs.add(playerdataDir.getPath());
    if (statsDir.exists())
      tarArgs.add(statsDir.getPath());
    if (advancementsDir.exists())
      tarArgs.add(advancementsDir.getPath());
    tarArgs.add("usercache.json");

    try {
      Process p = new ProcessBuilder(tarArgs).start();
      p.waitFor();
    } catch (Exception ignored) {
    }

    String oldUuid = null;
    String newUuid = null;

    try (FileReader fr = new FileReader(usercacheFile, StandardCharsets.UTF_8)) {
      JsonArray arr = JsonParser.parseReader(fr).getAsJsonArray();
      for (JsonElement el : arr) {
        if (el.isJsonObject()) {
          JsonObject obj = el.getAsJsonObject();
          if (obj.has("name") && obj.has("uuid")) {
            String name = obj.get("name").getAsString();
            String uuid = obj.get("uuid").getAsString();
            if (name.equalsIgnoreCase(oldName)) {
              oldUuid = uuid;
            }
            if (name.equalsIgnoreCase(newName)) {
              newUuid = uuid;
            }
          }
        }
      }
    } catch (Exception e) {
      return new ProcessResult(1, "Failed to parse usercache.json: " + e.getMessage());
    }

    if (oldUuid == null || newUuid == null) {
      return new ProcessResult(1,
          "UUID lookup failed. oldUuid=" + (oldUuid != null ? oldUuid : "not found") +
              ", newUuid=" + (newUuid != null ? newUuid : "not found") + " in usercache.json.");
    }

    StringBuilder log = new StringBuilder();

    File oldDat = new File(playerdataDir, oldUuid + ".dat");
    File newDat = new File(playerdataDir, newUuid + ".dat");
    File oldDatOld = new File(playerdataDir, oldUuid + ".dat_old");
    File newDatOld = new File(playerdataDir, newUuid + ".dat_old");

    if (oldDat.exists()) {
      oldDat.renameTo(newDat);
      if (oldDatOld.exists()) {
        oldDatOld.renameTo(newDatOld);
      }
      log.append("Migrated 'playerdata'.\n");
    } else {
      return new ProcessResult(1, "Files with UUID: '" + oldUuid + "' not found in playerdata.");
    }

    File oldStats = new File(statsDir, oldUuid + ".json");
    File newStats = new File(statsDir, newUuid + ".json");
    if (oldStats.exists()) {
      oldStats.renameTo(newStats);
      log.append("Migrated player stats.\n");
    }

    File oldAdv = new File(advancementsDir, oldUuid + ".json");
    File newAdv = new File(advancementsDir, newUuid + ".json");
    if (oldAdv.exists()) {
      oldAdv.renameTo(newAdv);
      log.append("Migrated player advancements.\n");
    }

    if (wasRunning) {
      ServerHandler.startServer(false, "4G", null, null, sess, serverDir);
      log.append("Server restarted.\n");
    }

    return new ProcessResult(0, log.toString().trim());
  }
}
