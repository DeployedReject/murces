package org.codeberg.DeployedReject;

import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class ServerHandler {

  public String type;
  public String loader;
  public String gVersion;
  public String lVersion;
  public int ram;

  public void serverHandler() {

    if (loader.equals("fabric")) {
      fabric();
    } else if (loader.equals("spigot")) {
    } else if (loader.equals("paper")) {
    } else if (loader.equals("forge")) {
    } else if (loader.equals("vanilla")) {
      vanilla();
    }

  }

  public void vanilla() {

    String url = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
    HttpRequest findingURL = HttpRequest.newBuilder()
        .uri(URI.create(url))
        .GET()
        .build();

    HttpResponse<String> temp;
    try {

      temp = Main.device.send(findingURL, BodyHandlers.ofString());

    } catch (Exception e) {
      ErrorHelper.errorJson(e.toString());
      return;
    }

    if (temp.statusCode() != 200) {
      ErrorHelper.errorJson("Website returned: " + temp.statusCode());
      return;
    }

    url = "null";
    JsonArray versions = JsonParser.parseString(temp.body()).getAsJsonObject().get("versions").getAsJsonArray();

    for (int i = 0; i < versions.size(); i++) {
      if (gVersion.equals(versions.get(i).getAsJsonObject().get("id").getAsString())) {
        url = versions.get(i).getAsJsonObject().get("url").getAsString();
        break;
      }
    }

    if (url.equals("null")) {
      ErrorHelper.errorJson("Not a valid version.");
      return;
    }

    findingURL = HttpRequest.newBuilder()
        .uri(URI.create(url))
        .GET()
        .build();

    try {

      temp = Main.device.send(findingURL, BodyHandlers.ofString());

    } catch (Exception e) {
      ErrorHelper.errorJson(e.toString());
      return;
    }

    if (temp.statusCode() != 200) {
      ErrorHelper.errorJson("Website returned status code:" + temp.statusCode());
      return;
    }

    JsonObject downloadUrl = JsonParser.parseString(temp.body()).getAsJsonObject();

    findingURL = HttpRequest.newBuilder()
        .uri(URI.create(
            downloadUrl.get("downloads").getAsJsonObject().get("server").getAsJsonObject().get("url").getAsString()))
        .GET()
        .build();

    HttpResponse<InputStream> downloading;

    try {

      downloading = Main.device.send(findingURL, BodyHandlers.ofInputStream());

    } catch (Exception e) {
      ErrorHelper.errorJson(e.toString());
      return;
    }

    if (downloading.statusCode() != 200) {
      ErrorHelper.errorJson("Website Returned status code: " + downloading.statusCode());
      return;
    }

    try (FileOutputStream downloaded = new FileOutputStream("server.jar")) {

      try {
        byte[] buffer = new byte[4096];
        long filesize = downloading.headers().firstValueAsLong("content-length").orElse(-1L);
        int readSize = 0;
        int bytesRead;
        InputStream writer = downloading.body();
        JsonObject response = new JsonObject();
        response.addProperty("status", 0);
        response.addProperty("type", "download");
        response.addProperty("progress", 0);

        while ((bytesRead = writer.read(buffer)) != -1) {
          downloaded.write(buffer, 0, bytesRead);
          readSize += bytesRead;
          response.addProperty("progress", (readSize * 100.0) / filesize);
          Communicator.printer(response);
        }
      } catch (Exception e) {
        ErrorHelper.errorJson(e.toString());
      }

    } catch (Exception e) {
      ErrorHelper.errorJson(e.toString());
    }
    spawnServer();

  }

  public void fabric() {

    String url = "https://meta.fabricmc.net/v2/versions/loader/" + gVersion + "/" + lVersion + "/1.1.1/server/jar";

    HttpRequest downloadRequest = HttpRequest
        .newBuilder()
        .uri(URI.create(url))
        .GET()
        .build();

    HttpResponse<InputStream> downloading;

    try {

      downloading = Main.device.send(downloadRequest, BodyHandlers.ofInputStream());

    } catch (Exception e) {
      ErrorHelper.errorJson(e.toString());
      return;
    }

    if (downloading.statusCode() != 200) {
      ErrorHelper.errorJson("Website Returned status code: " + downloading.statusCode());
      return;
    }

    try (FileOutputStream downloaded = new FileOutputStream("server.jar")) {

      try {
        byte[] buffer = new byte[4096];
        long filesize = downloading.headers().firstValueAsLong("content-length").orElse(-1L);
        int readSize = 0;
        int bytesRead;
        InputStream writer = downloading.body();
        JsonObject response = new JsonObject();
        response.addProperty("status", 0);
        response.addProperty("type", "download");
        response.addProperty("progress", 0);

        while ((bytesRead = writer.read(buffer)) != -1) {
          downloaded.write(buffer, 0, bytesRead);
          readSize += bytesRead;
          response.addProperty("progress", (readSize * 100.0) / filesize);
          Communicator.printer(response);
        }
      } catch (Exception e) {
        ErrorHelper.errorJson(e.toString());
      }

    } catch (Exception e) {
      ErrorHelper.errorJson(e.toString());
    }
    spawnServer();
  }

  public void spawnServer() {
    String[] command = new String[] {
        "tmux",
        "new-session",
        "-d",
        "-s",
        "mcServer",
        "java", // Split here!
        "-Xmx" + Integer.toString(ram) + "G",
        "-Xms" + Integer.toString(ram) + "G",
        "-jar",
        "server.jar",
        "nogui"
    };
    try {

      try (java.io.FileWriter eulaWriter = new java.io.FileWriter("eula.txt")) {
        eulaWriter.write("eula=true");
      } catch (Exception e) {
        ErrorHelper.errorJson(e.toString());
        return;
      }
      if (Shell.execute(command).waitFor() != 0) {
        ErrorHelper.errorJson("Server Not Started");

      } else {
        JsonObject response = new JsonObject();
        response.addProperty("status", 0);
        response.addProperty("type", "server");
        Communicator.printer(response);
      }
    } catch (Exception e) {
      ErrorHelper.errorJson(e.toString());
    }
  }

}
