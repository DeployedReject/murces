
package org.codeberg.DeployedReject.device;

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
import org.codeberg.DeployedReject.utils.ErrorHelper;
import org.codeberg.DeployedReject.utils.Communicator;
import org.codeberg.DeployedReject.utils.NetworkUtils;
import org.codeberg.DeployedReject.utils.Shell;

public class ServerHandler {

  public String type;
  public String loader;
  public String gVersion;
  public String lVersion;
  public int ram;
  public int job;// 1 for start,2 for stop, 0 for none,3 for check
  public JsonArray list = new JsonArray();

  public ServerHandler(String type, String loader, String gVersion, String lVersion, int ram, int job) {
    this.type = type;
    this.loader = loader;
    this.gVersion = gVersion;
    this.lVersion = lVersion;
    this.ram = ram;
    this.job = job;

  }

  public void serverHandler() {

    if (job == 1 || job == 0) {
      JsonObject startMsg = new JsonObject();
      startMsg.addProperty("status", 2);
      startMsg.addProperty("type", "server");
      Communicator.printer(startMsg);

      switch (loader) {
        case "fabric":
          fabric();
          break;
        case "spigot":
          spigot();
          break;
        case "paper":
          paper();
          break;
        case "forge":
          forge();
          break;
        case "vanilla":
          vanilla();
          break;
        default:
          ErrorHelper.errorJson("Server Unsupported or Mistyped");
          return;
      }
    }
    switch (job) {
      case 0:
        JsonObject comp = new JsonObject();
        comp.addProperty("status", 3);
        comp.addProperty("type", "server");
        Communicator.printer(comp);
        break;
      case 1:
        if (loader.equals("forge"))
          spawnServer(true);
        else
          spawnServer();
        break;
      case 2:
        stopServer();
        break;
      case 3:
        JsonObject response = new JsonObject();
        fabric();
        spigot();
        paper();
        vanilla();
        forge();
        response.add("server", list);
        response.add("serverList", list);
        response.addProperty("type", "server");
        response.addProperty("status", 0);
        Communicator.printer(response);
        break;
      case 4:
        boolean isRunning = false;
        try {
          String[] checkCmd = new String[] { "tmux", "has-session", "-t", "mcServer" };
          isRunning = (Shell.execute(checkCmd).waitFor() == 0);
        } catch (Exception ignored) {}
        JsonObject statusResp = new JsonObject();
        statusResp.addProperty("status", 0);
        statusResp.addProperty("type", "server");
        statusResp.addProperty("running", isRunning);
        Communicator.printer(statusResp);
        break;
      case 5:
        if (loader.equals("forge"))
          spawnServer(true);
        else
          spawnServer();
        break;
      case 6:
        stopServer();
        try {
          Thread.sleep(1000);
        } catch (Exception ignored) {}
        if (loader.equals("forge"))
          spawnServer(true);
        else
          spawnServer();
        break;
      default:
        ErrorHelper.errorJson("wtf is this job");
    }

  }

  private void vanilla() {
    if (job == 3) {
      list.add("vanilla");
      return;
    }

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

    long filesize = downloading.headers().firstValueAsLong("content-length").orElse(-1L);
    NetworkUtils.prog(downloading.body(), "server.jar", filesize);

  }

  private void fabric() {
    if (job == 3) {
      list.add("fabric");
      return;
    }

    String installerVersion = "1.1.2";
    try {
      HttpRequest vReq = HttpRequest.newBuilder().uri(URI.create("https://meta.fabricmc.net/v2/versions/installer")).GET().build();
      HttpResponse<String> vResp = NetworkUtils.attemptS(vReq);
      if (vResp != null && vResp.body() != null) {
        JsonArray arr = JsonParser.parseString(vResp.body()).getAsJsonArray();
        for (int i = 0; i < arr.size(); i++) {
          JsonObject inst = arr.get(i).getAsJsonObject();
          if (inst.has("stable") && inst.get("stable").getAsBoolean()) {
            installerVersion = inst.get("version").getAsString();
            break;
          }
        }
      }
    } catch (Exception ignored) {}

    String installerJar = "fabric-installer.jar";
    String url = "https://maven.fabricmc.net/net/fabricmc/fabric-installer/" + installerVersion + "/fabric-installer-" + installerVersion + ".jar";
    HttpRequest downloadRequest = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
    HttpResponse<InputStream> downloading = NetworkUtils.attemptI(downloadRequest);
    if (downloading == null) {
      ErrorHelper.errorJson("Failed to download Fabric installer");
      return;
    }

    long filesize = downloading.headers().firstValueAsLong("content-length").orElse(-1L);
    NetworkUtils.prog(downloading.body(), installerJar, filesize);

    java.util.List<String> commandList = new java.util.ArrayList<>();
    commandList.add("java");
    commandList.add("-jar");
    commandList.add(installerJar);
    commandList.add("server");
    commandList.add("-mcversion");
    commandList.add(gVersion);
    if (lVersion != null && !lVersion.isEmpty() && !lVersion.equalsIgnoreCase("none") && !lVersion.equalsIgnoreCase("latest")) {
      commandList.add("-loader");
      commandList.add(lVersion);
    }
    commandList.add("-downloadMinecraft");

    JsonObject response = new JsonObject();
    response.addProperty("status", 2);
    response.addProperty("type", "server");
    Communicator.printer(response);

    try {
      if (Shell.execute(commandList.toArray(new String[0])).waitFor() != 0) {
        ErrorHelper.errorJson("Fabric server installer execution failed");
        return;
      }
    } catch (Exception e) {
      ErrorHelper.errorJson("Fabric installation error: " + e.getMessage());
      return;
    } finally {
      try {
        Files.deleteIfExists(Paths.get(installerJar));
      } catch (Exception ignored) {}
    }
  }

  private void spigot() {
    if (job == 3) {
      list.add("spigot");
      return;
    }

    HttpResponse<InputStream> build;
    build = NetworkUtils.attemptI(HttpRequest.newBuilder()
        .uri(URI.create(
            "https://hub.spigotmc.org/jenkins/job/BuildTools/lastSuccessfulBuild/artifact/target/BuildTools.jar"))
        .GET().build());

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
      response.addProperty("type", "server");
      Communicator.printer(response);
      if (Shell.execute(command).waitFor() != 0) {
        ErrorHelper.errorJson("Build failed");
        return;
      }
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

  private void paper() {
    if (job == 3) {
      list.add("paper");
      return;
    }

    String url = "https://api.papermc.io/v2/projects/paper/versions/" + gVersion;

    HttpResponse<String> findingURL = NetworkUtils
        .attemptS(HttpRequest.newBuilder().uri(URI.create(url)).GET().build());
    int build = JsonParser.parseString(findingURL.body()).getAsJsonObject().get("builds").getAsJsonArray().size() - 1;

    build = JsonParser.parseString(findingURL.body()).getAsJsonObject().get("builds").getAsJsonArray().get(build)
        .getAsInt();

    url += "/builds/" + build + "/downloads/paper-" + gVersion + "-" + build + ".jar";

    HttpResponse<InputStream> downloading = NetworkUtils
        .attemptI(HttpRequest.newBuilder().uri(URI.create(url)).GET().build());

    long filesize = downloading.headers().firstValueAsLong("content-length").orElse(-1L);

    NetworkUtils.prog(downloading.body(), "server.jar", filesize);

  }

  private boolean checkCommand(String cmd) {
    try {
      Process p = new ProcessBuilder("which", cmd).start();
      return p.waitFor() == 0;
    } catch (Exception e) {
      return false;
    }
  }

  private void spawnServer() {
    if (!checkCommand("tmux")) {
      ErrorHelper.errorJson("tmux is not installed. Please install tmux (e.g., sudo apt install tmux).");
      return;
    }
    if (!checkCommand("java")) {
      ErrorHelper.errorJson("java is not found in PATH. Please install Java.");
      return;
    }

    String targetJar = "server.jar";
    if ("fabric".equalsIgnoreCase(loader) || Files.exists(Paths.get("fabric-server-launch.jar"))) {
      targetJar = "fabric-server-launch.jar";
    }

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
        targetJar,
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
      response.addProperty("type", "server");
      Communicator.printer(response);

      if (Shell.execute(command).waitFor() != 0) {
        ErrorHelper.errorJson("Server Already Started");
      } else {
        JsonObject serverDone = new JsonObject();
        serverDone.addProperty("status", 3);
        serverDone.addProperty("type", "server");
        Communicator.printer(serverDone);
      }
    } catch (Exception e) {
      ErrorHelper.errorJson(e.toString());
    }

  }

  private void stopServer() {

        // Verify that the tmux session exists before attempting to kill it
        String[] checkCmd = new String[] { "tmux", "has-session", "-t", "mcServer" };
        try {
            if (Shell.execute(checkCmd).waitFor() != 0) {
                // Session does not exist – nothing to stop
                ErrorHelper.errorJson("Server session not found; nothing to stop");
                return;
            }
        } catch (Exception e) {
            ErrorHelper.errorJson(e.toString());
            return;
        }

        // Attempt to kill the session gracefully
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

  private void forge() {
    if (job == 3) {
      list.add("forge");
      return;
    }

    String url = "https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json";
    HttpResponse<String> findingURl = NetworkUtils.attemptS(HttpRequest.newBuilder()
        .uri(URI.create(
            url))
        .GET().build());

    JsonObject result = JsonParser.parseString(findingURl.body()).getAsJsonObject();
    JsonObject promos = result.has("promos") ? result.getAsJsonObject("promos") : null;
    if (promos == null) {
      ErrorHelper.errorJson("No Forge promotions found.");
      return;
    }

    String iVersion = null;
    if (promos.has(gVersion + "-recommended")) {
      iVersion = promos.get(gVersion + "-recommended").getAsString();
    } else if (promos.has(gVersion + "-latest")) {
      iVersion = promos.get(gVersion + "-latest").getAsString();
    } else {
      ErrorHelper.errorJson("No Forge version found for " + gVersion);
      return;
    }

    String installerName = "forge-" + gVersion + "-" + iVersion + "-installer.jar";
    url = "https://maven.minecraftforge.net/net/minecraftforge/forge/" + gVersion + "-" + iVersion + "/forge-"
        + gVersion + "-" + iVersion + "-installer.jar";

    HttpResponse<InputStream> downloading = NetworkUtils
        .attemptI(HttpRequest.newBuilder().uri(URI.create(url)).GET().build());

    if (downloading == null) {
      ErrorHelper.errorJson("Failed to download Forge installer");
      return;
    }

    long filesize = downloading.headers().firstValueAsLong("content-length").orElse(-1L);

    NetworkUtils.prog(downloading.body(), installerName, filesize);

    String[] command = new String[] { "java", "-jar", installerName,
        "--installServer" };

    JsonObject response = new JsonObject();
    response.addProperty("status", 2);
    response.addProperty("type", "server");
    Communicator.printer(response);

    try {
      if (Shell.execute(command).waitFor() != 0) {
        ErrorHelper.errorJson("Error on my side probably");
        return;
      }
    } catch (Exception e) {
      ErrorHelper.errorJson(e.toString());
    } finally {
      try {
        Files.deleteIfExists(Paths.get(installerName));
      } catch (Exception ignored) {}
    }

  }

  private void spawnServer(boolean x) {
    if (!checkCommand("tmux")) {
      ErrorHelper.errorJson("tmux is not installed. Please install tmux (e.g., sudo apt install tmux).");
      return;
    }
    if (!checkCommand("java")) {
      ErrorHelper.errorJson("java is not found in PATH. Please install Java.");
      return;
    }

    JsonObject response = new JsonObject();
    response.addProperty("status", 2);
    response.addProperty("type", "server");
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
        return;
      }

    } catch (Exception e) {
      ErrorHelper.errorJson(e.toString());
      return;
    }

    JsonObject serverDone = new JsonObject();
    serverDone.addProperty("status", 3);
    serverDone.addProperty("type", "server");
    Communicator.printer(serverDone);

  }
}
