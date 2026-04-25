package org.codeberg.DeployedReject;

import java.util.Properties;
import java.util.Scanner;
import com.google.gson.JsonObject;
import java.lang.System;
import java.net.http.HttpClient;
import java.nio.file.Paths;
import java.nio.file.Files;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Main {

  public static HttpClient device = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();

  public static void main(String[] args) {

    try {
      if (!Files.isDirectory(Paths.get("mods")))
        Files.createDirectory(Paths.get("mods"));
    } catch (Exception e) {
      ErrorHelper.errorJson(e.toString());
    }

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

          if (!(request.has("gameVersion") && request.has("loaderVersion") && request.has("serverType")
              && request.has("ram"))) {
            ErrorHelper.errorJson("Missing One Or More Necessary Parameters.");
            return;
          }

          ServerHandler sh = new ServerHandler();

          sh.loader = request.get("serverType").getAsString();
          sh.gVersion = request.get("gameVersion").getAsString();
          sh.lVersion = request.get("loaderVersion").getAsString();
          sh.ram = request.get("ram").getAsInt();
          sh.serverHandler();

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
