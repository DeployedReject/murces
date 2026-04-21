
package org.codeberg.DeployedReject;

import java.net.MalformedURLException;
import java.util.Properties;
import java.util.Scanner;
import com.google.gson.JsonObject;
import java.lang.System;
import com.google.gson.JsonParser;
import org.codeberg.DeployedReject.errorHelper;
import org.codeberg.DeployedReject.modrinth;
import java.util.Properties;
import java.io.IOException;
import java.io.InputStream;
import org.codeberg.DeployedReject.serverDownload;

public class Main {
  public static void main(String[] args) {

    Properties env = new Properties();
    try (InputStream envStream = Main.class.getClassLoader().getResourceAsStream("config.properties")) {
      env.load(envStream);
      env.getProperty("curseAPI");
    } catch (Exception e) {
      errorHelper.errorJson("I probably forgot to load .env" + e.toString());
    }
    String curseAPI = env.getProperty("curseAPI");
    String email = env.getProperty("email");

    Scanner sc = new Scanner(System.in);

    while (true) {

      String temp = sc.nextLine();
      JsonObject request = JsonParser.parseString(temp).getAsJsonObject();

      // type can be modding/serverSetup/kill
      if (!request.has("type")) {
        errorHelper.errorJson("Error Missing A Type Parameter");
        continue;
      }
      String type = request.get("type").getAsString();

      if (type.equals("kill"))
        break;
      else if (type.equals("server")) {

        if (!(request.has("gameVersion") && request.has("serverVersion") && request.has("serverType")
            & request.has("subType"))) {
          errorHelper.errorJson("Missing One Or More Necessary Parameters.");
          continue;
        }

        serverDownload.gameVersion = request.get("gameVersion").getAsString();
        serverDownload.serverVersion = request.get("serverVersion").getAsString();
        serverDownload.serverType = request.get("serverType").getAsString(); // Can be Fabric, Forge, Spigot and PaperMC
        serverDownload.type = request.get("subType").getAsString();
        serverDownload.serverHandler();
      } else if (type.equals("modding")) {

        if (!(request.has("modBrowser") && request.has("modName") && request.has("version")
            && request.has("modLoader"))) {
          errorHelper.errorJson("Missing One or More Necessary Parameters.");
          continue;
        }

        String modBrowser = request.get("modBrowser").getAsString();

        if (modBrowser.equalsIgnoreCase("modrinth")) {
          modrinth.type = request.get("subType").getAsString();
          modrinth.query = request.get("modName").getAsString();
          modrinth.version = request.get("version").getAsString();
          modrinth.loader = request.get("modLoader").getAsString();
          modrinth.email = email;
          modrinth.modrinthHandler();
        } else if (modBrowser.equals("curseForge")) {
          // ToDo
        }
      } else {
        errorHelper.errorJson("Invalid Type");
      }

    }
    sc.close();
  }
}
