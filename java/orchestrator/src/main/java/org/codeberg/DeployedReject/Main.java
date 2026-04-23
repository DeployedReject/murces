package org.codeberg.DeployedReject;

import java.util.Properties;
import java.util.Scanner;
import com.google.gson.JsonObject;
import java.lang.System;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Main {
  public static void main(String[] args) {

    Properties env = new Properties();
    try (InputStream envStream = Main.class.getClassLoader().getResourceAsStream("config.properties")) {
      env.load(envStream);
      env.getProperty("curseAPI");
    } catch (Exception e) {
      ErrorHelper.errorJson("I probably forgot to load .env" + e.toString());
    }
    String curseAPI = env.getProperty("curseAPI");
    String email = env.getProperty("email");

    Scanner sc = new Scanner(System.in);

    ExecutorService worker = Executors.newCachedThreadPool();
    Communicator.startPrinter();

    while (true) {

      String temp = sc.nextLine();
      JsonObject request = JsonParser.parseString(temp).getAsJsonObject();

      if (request.get("type").getAsString().equals("kill"))
        break;

      worker.submit(() -> {
        // type can be modding/serverSetup/kill
        if (!request.has("type")) {
          ErrorHelper.errorJson("Error Missing A Type Parameter");
          return;
        }
        String type = request.get("type").getAsString();

        if (type.equals("server")) {

          if (!(request.has("gameVersion") && request.has("serverVersion") && request.has("serverType")
              && request.has("subType"))) {
            ErrorHelper.errorJson("Missing One Or More Necessary Parameters.");
            return;
          }

          ServerDownloader sd = new ServerDownloader();

          sd.gameVersion = request.get("gameVersion").getAsString();
          sd.serverVersion = request.get("serverVersion").getAsString();
          sd.serverType = request.get("serverType").getAsString(); // Can be Fabric, Forge, Spigot and PaperMC
          sd.type = request.get("subType").getAsString();
          sd.serverHandler();
        } else if (type.equals("modding")) {

          if (!(request.has("modBrowser") && request.has("modName") && request.has("version")
              && request.has("modLoader") && request.has("modId"))) {
            ErrorHelper.errorJson("Missing One or More Necessary Parameters.");
            return;
          }

          String modBrowser = request.get("modBrowser").getAsString();

          if (modBrowser.equalsIgnoreCase("modrinth")) {

            Modrinth mb = new Modrinth();
            mb.type = request.get("subType").getAsString();
            mb.query = request.get("modName").getAsString();
            mb.version = request.get("version").getAsString();
            mb.loader = request.get("modLoader").getAsString();
            mb.email = email;
            mb.modrinthHandler();
          } else if (modBrowser.equals("curseForge")) {
            CurseForge cf = new CurseForge();
            cf.type = request.get("subType").getAsString();
            cf.modName = request.get("modName").getAsString();
            cf.version = request.get("version").getAsString();
            cf.loader = request.get("modLoader").getAsString();
            cf.API = curseAPI;
            cf.modId = request.get("modId").getAsString();
            cf.curseForgeHandler();

          }
        } else {
          ErrorHelper.errorJson("Invalid Type");
        }
      });

    }
    sc.close();
  }
}
