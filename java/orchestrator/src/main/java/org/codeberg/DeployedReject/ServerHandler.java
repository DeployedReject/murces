package org.codeberg.DeployedReject;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.stream.Stream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

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
      spigot();
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
    long filesize = downloading.headers().firstValueAsLong("content-length").orElse(-1L);
    Progress.prog(downloading.body(), "server.jar", filesize);

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
      ErrorHelper.errorJson("Website Returned Status Code: " + downloading.statusCode());
      return;
    }

    long filesize = downloading.headers().firstValueAsLong("content-length").orElse(-1L);
    Progress.prog(downloading.body(), "server.jar", filesize);

    spawnServer();
  }

  public void spigot() {

    HttpResponse<InputStream> build;
    try {
      build = Main.device.send(HttpRequest.newBuilder()
          .uri(URI.create(
              "https://hub.spigotmc.org/jenkins/job/BuildTools/lastSuccessfulBuild/artifact/target/BuildTools.jar"))
          .GET().build(), BodyHandlers.ofInputStream());
    } catch (Exception e) {
      ErrorHelper.errorJson(e.toString());
      return;
    }
    if (build.statusCode() != 200) {
      ErrorHelper.errorJson("Website Returned Status Code: " + build.statusCode());
      return;
    }

    long filesize = build.headers().firstValueAsLong("content-length").orElse(-1L);
    Progress.prog(build.body(), "BuildTools.jar", filesize);

    String[] command = new String[] {
        "java",
        "-Xmx" + Integer.toString(ram) + "G",
        "-Xms" + Integer.toString(ram) + "G",
        "-jar",
        "BuildTools.jar",
        "--rev",
        gVersion
    };

    try {
      JsonObject response = new JsonObject();
      response.addProperty("status", 2);
      Communicator.printer(response);
      if (Shell.execute(command).waitFor() != 0) {
        ErrorHelper.errorJson("Build failed");
        return;
      }
      response.addProperty("status", 3);
      Communicator.printer(response);
    } catch (Exception e) {
      ErrorHelper.errorJson(e.toString());
    }

    try (Stream<Path> jars = Files.list(Paths.get("."))) {
      jars.forEach((Path jar) -> {
        try {
          if (jar.getFileName().toString().startsWith("spigot-" + gVersion)) {
            Files.move(jar, Paths.get("server.jar"), StandardCopyOption.REPLACE_EXISTING);
          }
        } catch (Exception e) {
          ErrorHelper.errorJson("Could not rename spigot.");
        }
      });

    } catch (Exception e) {
      ErrorHelper.errorJson("Ignore this most of the time.");
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

      JsonObject response = new JsonObject();
      response.addProperty("status", 2);
      Communicator.printer(response);

      if (Shell.execute(command).waitFor() != 0) {
        ErrorHelper.errorJson("Server Not Started");

      } else {
        response.addProperty("status", 3);
        response.addProperty("type", "server");
        Communicator.printer(response);
      }
    } catch (Exception e) {
      ErrorHelper.errorJson(e.toString());
    }
  }

}
