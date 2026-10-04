package org.codeberg.DeployedReject;

import java.util.Properties;
import java.util.Scanner;
import com.google.gson.JsonObject;
import java.net.http.HttpClient;
import java.nio.file.Paths;
import java.nio.file.Files;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.codeberg.DeployedReject.utils.Communicator;
import org.codeberg.DeployedReject.utils.ErrorHelper;
import org.codeberg.DeployedReject.device.ServerHandler;
import org.codeberg.DeployedReject.mods.Modrinth;
import org.codeberg.DeployedReject.mods.CurseForge;

public class Main {
  public static HttpClient device = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();

  public static void main(String[] args) {

    System.setErr(new PrintStream(OutputStream.nullOutputStream()));

    try {
      if (!Files.isDirectory(Paths.get("mods")))
        Files.createDirectory(Paths.get("mods"));
    } catch (Exception e) {
      ErrorHelper.errorJson("Failed to initialize 'mods' directory: " + (e.getMessage() != null ? e.getMessage() : e.toString()));
    }

    Properties env = new Properties();
    try (InputStream envStream = Main.class.getClassLoader().getResourceAsStream("config.properties")) {
      if (envStream != null) {
        env.load(envStream);
      }
    } catch (Exception e) {
      ErrorHelper.errorJson("Failed to load configuration properties: " + (e.getMessage() != null ? e.getMessage() : e.toString()));
    }
    String curseAPI = env.getProperty("curseAPI");
    String email = env.getProperty("email");

    Scanner sc = new Scanner(System.in);

    ExecutorService worker = Executors.newCachedThreadPool();
    Communicator.startPrinter();

    JsonObject bootup = new JsonObject();
    bootup.addProperty("status", 10);
    Communicator.printer(bootup);

    while (true) {

      String temp = sc.nextLine();
      JsonObject request = JsonParser.parseString(temp).getAsJsonObject();

      if (request.get("type").getAsString().equals("kill"))
        break;

      worker.submit(() -> {

        if (!request.has("type")) {
          ErrorHelper.errorJson("Error Missing A Type Parameter");
          return;
        }
        String type = request.get("type").getAsString();

        if (type.equals("server")) {

          if (!(request.has("gameVersion") && request.has("loaderVersion") && request.has("serverType")
              && request.has("ram") && request.has("job"))) {
            ErrorHelper.errorJson("Missing One Or More Necessary Parameters.");
            return;
          }
          String loader = request.get("serverType").getAsString();
          String gVersion = request.get("gameVersion").getAsString();
          String lVersion = request.get("loaderVersion").getAsString();
          int ram = request.get("ram").getAsInt();
          int job = request.get("job").getAsInt();

          ServerHandler sh = new ServerHandler(type, loader, gVersion, lVersion, ram, job);
          if (request.has("command")) sh.command = request.get("command").getAsString();
          if (request.has("oldName")) sh.oldName = request.get("oldName").getAsString();
          if (request.has("newName")) sh.newName = request.get("newName").getAsString();
          if (request.has("sourceFolder")) sh.sourceFolder = request.get("sourceFolder").getAsString();
          if (request.has("targetFolder")) sh.targetFolder = request.get("targetFolder").getAsString();
          if (request.has("retentionLimit")) sh.retentionLimit = request.get("retentionLimit").getAsInt();
          if (request.has("cloudSync")) sh.cloudSync = request.get("cloudSync").getAsBoolean();
          if (request.has("cloudRemote")) sh.cloudRemote = request.get("cloudRemote").getAsString();

          sh.serverHandler();

        } else if (type.equals("backup")) {
          String src = request.has("sourceFolder") ? request.get("sourceFolder").getAsString() : "world";
          String tgt = request.has("targetFolder") ? request.get("targetFolder").getAsString() : "backup";
          int ret = request.has("retentionLimit") ? request.get("retentionLimit").getAsInt() : 3;
          boolean sync = request.has("cloudSync") && request.get("cloudSync").getAsBoolean();
          String remote = request.has("cloudRemote") ? request.get("cloudRemote").getAsString() : "minecraftdrive";
          org.codeberg.DeployedReject.utils.ProcessResult res =
              org.codeberg.DeployedReject.device.BackupHandler.runBackup(src, tgt, ret, sync, remote);
          JsonObject resp = new JsonObject();
          resp.addProperty("status", res.exitCode == 0 ? 0 : 1);
          resp.addProperty("type", "backup");
          resp.addProperty("output", res.output);
          Communicator.printer(resp);

        } else if (type.equals("modding")) {
          if (!(request.has("modBrowser") && request.has("modName") && request.has("version")
              && (request.has("loader") || request.has("modLoader")) && request.has("subType"))) {
            ErrorHelper.errorJson("Missing One or More Necessary Parameters.");
            return;
          }

          String modBrowser = request.get("modBrowser").getAsString();
          String subType = request.get("subType").getAsString();
          String modName = request.get("modName").getAsString();
          String version = request.get("version").getAsString();
          String loader = request.has("loader") ? request.get("loader").getAsString() : request.get("modLoader").getAsString();

          ModAPI handle;
          switch (modBrowser) {
            case "modrinth":
              handle = new Modrinth(subType, modName, version, loader, email);
              break;
            case "curseForge":
              if (subType.equals("download") && !request.has("modId")) {
                ErrorHelper.errorJson("Missing One or More Necessary Parameters.");
                return;
              }
              handle = new CurseForge(subType, modName, version, loader, curseAPI, request.get("modId").getAsString());
              break;
            default:
              ErrorHelper.errorJson("Mod API not Supported or Incorrectly Typed");
              return;
          }
          handle.handler();

        } else {
          ErrorHelper.errorJson("Invalid Type");
        }
      });

    }
    worker.shutdownNow();
    sc.close();
  }
}
