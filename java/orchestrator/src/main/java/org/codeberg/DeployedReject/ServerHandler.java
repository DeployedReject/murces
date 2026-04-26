package org.codeberg.DeployedReject;

import java.io.FileWriter;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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
  public int job;// 1 for start,2 for stop, 0 for none,3 for check
  public JsonArray list = new JsonArray();

  public void serverHandler() {

    if (job == 1 || job == 0) {
      if (loader.equals("fabric")) {
        fabric();
      } else if (loader.equals("spigot")) {
        spigot();
      } else if (loader.equals("paper")) {
        paper();
      } else if (loader.equals("forge")) {
        forge();
        // To Do
      } else if (loader.equals("vanilla")) {
        vanilla();
      }
    }
    if (job == 1) {

      if (loader.equals("forge"))
        spawnServer(true);
      else
        spawnServer();
    } else if (job == 2)
      stopServer();
    else if (job == 3) {
      JsonObject response = new JsonObject();
      fabric();
      spigot();
      paper();
      vanilla();
      forge();
      response.add("serverList", list);
      Communicator.printer(response);
    }

  }

  public void vanilla() {

    String url = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
    HttpRequest findingURL = HttpRequest.newBuilder()
        .uri(URI.create(url))
        .GET()
        .build();

    HttpResponse<String> temp;
    temp = NetworkUtils.attemptS(findingURL);

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

    temp = NetworkUtils.attemptS(findingURL);

    JsonObject downloadUrl = JsonParser.parseString(temp.body()).getAsJsonObject();

    findingURL = HttpRequest.newBuilder()
        .uri(URI.create(
            downloadUrl.get("downloads").getAsJsonObject().get("server").getAsJsonObject().get("url").getAsString()))
        .GET()
        .build();

    HttpResponse<InputStream> downloading;

    downloading = NetworkUtils.attemptI(findingURL);

    if (job == 3) {
      list.add("vanilla");
      return;

    }
    long filesize = downloading.headers().firstValueAsLong("content-length").orElse(-1L);
    NetworkUtils.prog(downloading.body(), "server.jar", filesize);

  }

  public void fabric() {

    String url = "https://meta.fabricmc.net/v2/versions/loader/" + gVersion + "/" + lVersion + "/1.1.1/server/jar";

    HttpRequest downloadRequest = HttpRequest
        .newBuilder()
        .uri(URI.create(url))
        .GET()
        .build();

    HttpResponse<InputStream> downloading;

    downloading = NetworkUtils.attemptI(downloadRequest);

    if (job == 3) {
      list.add("fabric");
      return;
    }

    long filesize = downloading.headers().firstValueAsLong("content-length").orElse(-1L);
    NetworkUtils.prog(downloading.body(), "server.jar", filesize);

  }

  public void spigot() {

    HttpResponse<InputStream> build;
    build = NetworkUtils.attemptI(HttpRequest.newBuilder()
        .uri(URI.create(
            "https://hub.spigotmc.org/jenkins/job/BuildTools/lastSuccessfulBuild/artifact/target/BuildTools.jar"))
        .GET().build());
    if (job == 3) {
      list.add("spigot");
      return;
    }

    long filesize = build.headers().firstValueAsLong("content-length").orElse(-1L);
    NetworkUtils.prog(build.body(), "BuildTools.jar", filesize);

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

  }

  public void paper() {
    String url = "https://api.papermc.io/v2/projects/paper/versions/" + gVersion;

    HttpResponse<String> findingURL = NetworkUtils
        .attemptS(HttpRequest.newBuilder().uri(URI.create(url)).GET().build());
    int build = JsonParser.parseString(findingURL.body()).getAsJsonObject().get("builds").getAsJsonArray().size() - 1;

    build = JsonParser.parseString(findingURL.body()).getAsJsonObject().get("builds").getAsJsonArray().get(build)
        .getAsInt();

    url += "/builds/" + build + "/downloads/paper-" + gVersion + "-" + build + ".jar";

    HttpResponse<InputStream> downloading = NetworkUtils
        .attemptI(HttpRequest.newBuilder().uri(URI.create(url)).GET().build());

    if (job == 3) {
      list.add("paper");
      return;
    }
    long filesize = downloading.headers().firstValueAsLong("content-length").orElse(-1L);

    NetworkUtils.prog(downloading.body(), "server.jar", filesize);

  }

  public void spawnServer() {
    String[] command = new String[] {
        "tmux",
        "new-session",
        "-d",
        "-s",
        "mcServer",
        "java",
        "-Xmx" + Integer.toString(ram) + "G",
        "-Xms" + Integer.toString(ram) + "G",
        "-XX:+UseG1GC",
        "-XX:+ParallelRefProcEnabled",
        "-XX:MaxGCPauseMillis=200",
        "-XX:+UnlockExperimentalVMOptions",
        "-XX:+DisableExplicitGC",
        "-XX:+AlwaysPreTouch",
        "-XX:G1NewSizePercent=30",
        "-XX:G1MaxNewSizePercent=40",
        "-XX:G1HeapRegionSize=8M",
        "-XX:G1ReservePercent=20",
        "-XX:G1HeapWastePercent=5",
        "-XX:G1MixedGCCountTarget=4",
        "-XX:InitiatingHeapOccupancyPercent=15",
        "-XX:G1MixedGCLiveThresholdPercent=90",
        "-XX:G1RSetUpdatingPauseTimePercent=5",
        "-XX:SurvivorRatio=32",
        "-XX:+PerfDisableSharedMem",
        "-XX:MaxTenuringThreshold=1",
        "-Dusing.aikars.flags=https://mcflags.emc.gs",
        "-Daikars.new.flags=true",
        "-jar",
        "server.jar",
        "--nogui"
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
        ErrorHelper.errorJson("Server Already Started");

      } else {
        response.addProperty("status", 3);
        response.addProperty("type", "server");
        Communicator.printer(response);
      }
    } catch (Exception e) {
      ErrorHelper.errorJson(e.toString());
    }

  }

  public void stopServer() {

    String[] command = new String[] {
        "tmux", "kill-session", "-t", "mcServer"
    };

    try {
      if (Shell.execute(command).waitFor() != 0) {
        ErrorHelper.errorJson("Could Not Stop Server");
        return;
      }

      JsonObject response = new JsonObject();
      response.addProperty("status", 0);
      response.addProperty("type", "server");
      Communicator.printer(response);

    } catch (Exception e) {
      ErrorHelper.errorJson(e.toString());
    }

  }

  public void forge() {
    String url = "https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json";
    HttpResponse<String> findingURl = NetworkUtils.attemptS(HttpRequest.newBuilder()
        .uri(URI.create(
            url))
        .GET().build());

    JsonObject result = JsonParser.parseString(findingURl.body()).getAsJsonObject();

    String iVersion = result.get("promos").getAsJsonObject().get(gVersion + "-recommended").getAsString();

    url = "https://maven.minecraftforge.net/net/minecraftforge/forge/" + gVersion + "-" + iVersion + "/forge-"
        + gVersion + "-" + iVersion + "-installer.jar";

    HttpResponse<InputStream> downloading = NetworkUtils
        .attemptI(HttpRequest.newBuilder().uri(URI.create(url)).GET().build());

    if (job == 3) {
      list.add("forge");
      return;
    }

    long filesize = downloading.headers().firstValueAsLong("content-length").orElse(-1L);

    NetworkUtils.prog(downloading.body(), "forge-" + gVersion + "-" + iVersion + "-installer.jar", filesize);

    String[] command = new String[] { "java", "-jar", "forge-" + gVersion + "-" + iVersion + "-installer.jar",
        "--installServer" };

    JsonObject response = new JsonObject();
    response.addProperty("status", 2);
    Communicator.printer(response);

    try {
      if (Shell.execute(command).waitFor() != 0) {
        ErrorHelper.errorJson("Error on my side probably");
        return;
      }
    } catch (Exception e) {
      ErrorHelper.errorJson(e.toString());
    }

    response.addProperty("status", 3);
    Communicator.printer(response);

  }

  public void spawnServer(boolean x) {

    JsonObject response = new JsonObject();
    response.addProperty("status", 2);
    Communicator.printer(response);

    try (FileWriter eulaWriter = new FileWriter("eula.txt")) {
      eulaWriter.write("eula=true");
    } catch (Exception e) {
      ErrorHelper.errorJson(e.toString());
      return;
    }

    try (FileWriter argsWriter = new FileWriter("user_jvm_args.txt")) {
      argsWriter.write("-Xmx" + Integer.toString(ram) + "G\n" +
          "-Xms" + Integer.toString(ram) + "G\n" +
          "-XX:+UseG1GC\n" +
          "-XX:+ParallelRefProcEnabled\n" +
          "-XX:MaxGCPauseMillis=200\n" +
          "-XX:+UnlockExperimentalVMOptions\n" +
          "-XX:+DisableExplicitGC\n" +
          "-XX:+AlwaysPreTouch\n" +
          "-XX:G1NewSizePercent=30\n" +
          "-XX:G1MaxNewSizePercent=40\n" +
          "-XX:G1HeapRegionSize=8M\n" +
          "-XX:G1ReservePercent=20\n" +
          "-XX:G1HeapWastePercent=5\n" +
          "-XX:G1MixedGCCountTarget=4\n" +
          "-XX:InitiatingHeapOccupancyPercent=15\n" +
          "-XX:G1MixedGCLiveThresholdPercent=90\n" +
          "-XX:G1RSetUpdatingPauseTimePercent=5\n" +
          "-XX:SurvivorRatio=32\n" +
          "-XX:+PerfDisableSharedMem\n" +
          "-XX:MaxTenuringThreshold=1\n" +
          "-Dusing.aikars.flags=https://mcflags.emc.gs\n" +
          "-Daikars.new.flags=true");

    } catch (Exception e) {
      ErrorHelper.errorJson(e.toString());
    }

    try {
      String[] command = new String[] { "chmod", "+x", "run.sh" };
      if (Shell.execute(command).waitFor() != 0) {
        ErrorHelper.errorJson("User not authorized.");
        return;
      }

      command = new String[] { "tmux", "new-session", "-d", "-s", "mcServer", "./run.sh", "nogui" };

      if (Shell.execute(command).waitFor() != 0) {
        ErrorHelper.errorJson("too tired to write a error message.");
      }

    } catch (Exception e) {
      ErrorHelper.errorJson(e.toString());
    }

    response.addProperty("status", 3);
    Communicator.printer(response);

  }
}
