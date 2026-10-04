
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
import org.codeberg.DeployedReject.utils.ProcessResult;
import org.codeberg.DeployedReject.utils.Shell;

import java.io.File;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ServerHandler {

  public String type;
  public String loader;
  public String gVersion;
  public String lVersion;
  public int ram;
  public int job;
  public String command;
  public String oldName;
  public String newName;
  public String sourceFolder;
  public String targetFolder;
  public int retentionLimit = 3;
  public boolean cloudSync = false;
  public String cloudRemote = "minecraftdrive";
  public JsonArray list = new JsonArray();

  public ServerHandler(String type, String loader, String gVersion, String lVersion, int ram, int job) {
    this.type = type;
    this.loader = loader;
    this.gVersion = gVersion;
    this.lVersion = lVersion;
    this.ram = ram;
    this.job = job;
  }

  public static boolean isCommandAvailable(String cmd) {
    try {
      Process p = new ProcessBuilder("which", cmd).start();
      return p.waitFor() == 0;
    } catch (Exception e) {
      return false;
    }
  }

  public static boolean isServerRunning() {
    try {
      Process p = new ProcessBuilder("tmux", "has-session", "-t", "mcsv").start();
      if (p.waitFor() == 0)
        return true;
      p = new ProcessBuilder("tmux", "has-session", "-t", "mcServer").start();
      if (p.waitFor() == 0)
        return true;
      p = new ProcessBuilder("pgrep", "-f", "server.jar").start();
      if (p.waitFor() == 0)
        return true;
      p = new ProcessBuilder("pgrep", "-f", "fabric-server-launch.jar").start();
      return p.waitFor() == 0;
    } catch (Exception e) {
      return false;
    }
  }

  public static ProcessResult startServer(boolean publicTunnel) {
    return startServer(publicTunnel, "4G");
  }

  public static ProcessResult startServer(boolean publicTunnel, String ram) {
    if (!isCommandAvailable("tmux")) {
      return new ProcessResult(1,
          "[ERROR] 'tmux' is not installed or not in PATH.\nMurces requires tmux to manage background Minecraft sessions.\nPlease install it (e.g. 'sudo apt install tmux' or 'pacman -S tmux').");
    }
    if (!isCommandAvailable("java")) {
      return new ProcessResult(1,
          "[ERROR] 'java' is not found in PATH.\nPlease install Java (e.g. OpenJDK 17/21+) to run Minecraft servers.");
    }
    if (isServerRunning()) {
      return new ProcessResult(1, "Minecraft server session 'mcsv' is already running.");
    }

    try (FileWriter eulaWriter = new FileWriter("eula.txt")) {
      eulaWriter.write("eula=true\n");
    } catch (Exception ignored) {
    }

    String ramArg = (ram != null && !ram.trim().isEmpty()) ? ram.trim() : "4G";
    if (ramArg.matches("^[0-9]+$")) {
      ramArg = ramArg + "G";
    }

    if (publicTunnel) {
      try {
        Process p = new ProcessBuilder("tmux", "has-session", "-t", "playit").start();
        if (p.waitFor() != 0) {
          String playitBin = new File("./playit").canExecute() ? "./playit" : "playit";
          new ProcessBuilder("tmux", "new-session", "-d", "-s", "playit",
              playitBin + " --secret_path ./playit.toml start").start().waitFor();
        }
      } catch (Exception ignored) {
      }
    }

    File userJvmArgs = new File("user_jvm_args.txt");
    if (userJvmArgs.exists()) {
      try {
        String content = Files.readString(userJvmArgs.toPath(), StandardCharsets.UTF_8);
        content = content.replaceAll("-Xmx[0-9]*[GM]", "-Xmx" + ramArg)
            .replaceAll("-Xms[0-9]*[GM]", "-Xms" + ramArg);
        Files.writeString(userJvmArgs.toPath(), content, StandardCharsets.UTF_8);
      } catch (Exception ignored) {
      }
    }

    File runSh = new File("run.sh");
    if (runSh.exists()) {
      try {
        new ProcessBuilder("chmod", "+x", "run.sh").start().waitFor();
        Process p = new ProcessBuilder("tmux", "new-session", "-d", "-s", "mcsv", "./run.sh", "nogui").start();
        int code = p.waitFor();
        if (code == 0) {
          return new ProcessResult(0, "Minecraft server started in tmux session 'mcsv'.");
        } else {
          return new ProcessResult(code, "Failed to start server tmux session (code " + code + ").");
        }
      } catch (Exception e) {
        return new ProcessResult(1, "Failed to launch run.sh: " + e.getMessage());
      }
    }

    String targetJar = "server.jar";
    if (new File("fabric-server-launch.jar").exists()) {
      targetJar = "fabric-server-launch.jar";
    }

    List<String> cmd = new ArrayList<>(Arrays.asList(
        "tmux", "new-session", "-d", "-s", "mcsv",
        "java",
        "-Xmx" + ramArg,
        "-Xms" + ramArg,
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
        "nogui"));

    try {
      Process p = new ProcessBuilder(cmd).start();
      int code = p.waitFor();
      if (code == 0) {
        return new ProcessResult(0, "Minecraft server started in tmux session 'mcsv'.");
      } else {
        return new ProcessResult(code, "Failed to start server tmux session (code " + code + ").");
      }
    } catch (Exception e) {
      return new ProcessResult(1, "Failed to launch server session: " + e.getMessage());
    }
  }

  public static ProcessResult stopServer() {
    if (!isCommandAvailable("tmux")) {
      return new ProcessResult(1, "[ERROR] 'tmux' is not installed or not in PATH.");
    }

    boolean hasSession = false;
    try {
      Process p = new ProcessBuilder("tmux", "has-session", "-t", "mcsv").start();
      hasSession = (p.waitFor() == 0);
    } catch (Exception ignored) {
    }

    if (!hasSession) {
      return new ProcessResult(0, "Minecraft server session 'mcsv' is not running.");
    }

    try {
      new ProcessBuilder("tmux", "send-keys", "-t", "mcsv", "stop", "C-m").start().waitFor();

      for (int i = 0; i < 30; i++) {
        Thread.sleep(500);
        Process p = new ProcessBuilder("tmux", "has-session", "-t", "mcsv").start();
        if (p.waitFor() != 0) {
          return new ProcessResult(0, "Minecraft server stopped.");
        }
      }

      new ProcessBuilder("tmux", "kill-session", "-t", "mcsv").start().waitFor();
      return new ProcessResult(0, "Minecraft server session terminated.");
    } catch (Exception e) {
      return new ProcessResult(1, "Failed to stop server: " + e.getMessage());
    }
  }

  public static ProcessResult sendConsoleCommand(String cmd) {
    if (!isCommandAvailable("tmux")) {
      return new ProcessResult(1, "[ERROR] 'tmux' is not installed or not in PATH.");
    }
    try {
      Process p = new ProcessBuilder("tmux", "has-session", "-t", "mcsv").start();
      if (p.waitFor() != 0) {
        return new ProcessResult(1, "Minecraft server session 'mcsv' not found.");
      }
      new ProcessBuilder("tmux", "send-keys", "-t", "mcsv", cmd, "C-m").start().waitFor();
      return new ProcessResult(0, "Command dispatched to Minecraft server.");
    } catch (Exception e) {
      return new ProcessResult(1, "Failed to send command: " + e.getMessage());
    }
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
        try (FileWriter eulaWriter = new FileWriter("eula.txt")) {
          eulaWriter.write("eula=true\n");
        } catch (Exception ignored) {
        }
        JsonObject comp = new JsonObject();
        comp.addProperty("status", 3);
        comp.addProperty("type", "server");
        Communicator.printer(comp);
        break;
      case 1:
        startServer(false, String.valueOf(ram) + "G");
        break;
      case 2:
        stopServer();
        JsonObject stopResp = new JsonObject();
        stopResp.addProperty("status", 0);
        stopResp.addProperty("type", "server");
        Communicator.printer(stopResp);
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
        JsonObject statusResp = new JsonObject();
        statusResp.addProperty("status", 0);
        statusResp.addProperty("type", "server");
        statusResp.addProperty("running", isServerRunning());
        Communicator.printer(statusResp);
        break;
      case 5:
        startServer(false, String.valueOf(ram) + "G");
        break;
      case 6:
        stopServer();
        try {
          Thread.sleep(1000);
        } catch (Exception ignored) {
        }
        startServer(false, String.valueOf(ram) + "G");
        break;
      case 7:
        ProcessResult cmdRes = sendConsoleCommand(command != null ? command : "");
        JsonObject cmdResp = new JsonObject();
        cmdResp.addProperty("status", cmdRes.exitCode == 0 ? 0 : 1);
        cmdResp.addProperty("type", "server");
        cmdResp.addProperty("output", cmdRes.output);
        Communicator.printer(cmdResp);
        break;
      case 8:
        ProcessResult migRes = MigrationHandler.migratePlayer(oldName, newName);
        JsonObject migResp = new JsonObject();
        migResp.addProperty("status", migRes.exitCode == 0 ? 0 : 1);
        migResp.addProperty("type", "server");
        migResp.addProperty("output", migRes.output);
        Communicator.printer(migResp);
        break;
      case 9:
        ProcessResult bakRes = BackupHandler.runBackup(sourceFolder, targetFolder, retentionLimit, cloudSync, cloudRemote);
        JsonObject bakResp = new JsonObject();
        bakResp.addProperty("status", bakRes.exitCode == 0 ? 0 : 1);
        bakResp.addProperty("type", "server");
        bakResp.addProperty("output", bakRes.output);
        Communicator.printer(bakResp);
        break;
      default:
        ErrorHelper.errorJson("Unrecognized server job action requested: " + job);
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
      ErrorHelper.errorJson("Minecraft version '" + gVersion + "' is not a valid Vanilla release.");
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

    if (!checkCommand("java")) {
      ErrorHelper.errorJson("Java runtime not found in PATH. Please install Java (JDK 17/21).");
      return;
    }

    if (job == 3) {
      list.add("fabric");
      return;
    }

    String installerVersion = "1.1.2";
    try {
      HttpRequest vReq = HttpRequest.newBuilder().uri(URI.create("https://meta.fabricmc.net/v2/versions/installer"))
          .GET().build();
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
    } catch (Exception ignored) {
    }

    String installerJar = "fabric-installer.jar";
    String url = "https://maven.fabricmc.net/net/fabricmc/fabric-installer/" + installerVersion + "/fabric-installer-"
        + installerVersion + ".jar";
    HttpRequest downloadRequest = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
    HttpResponse<InputStream> downloading = NetworkUtils.attemptI(downloadRequest);
    if (downloading == null) {
      ErrorHelper.errorJson("Failed to download Fabric installer from meta repository.");
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
    if (lVersion != null && !lVersion.isEmpty() && !lVersion.equalsIgnoreCase("none")
        && !lVersion.equalsIgnoreCase("latest")) {
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
        ErrorHelper.errorJson("Fabric installer exited with an error. Please verify Java compatibility and internet connection.");
        return;
      }
    } catch (Exception e) {
      ErrorHelper.errorJson("Fabric installation failed: " + (e.getMessage() != null ? e.getMessage() : e.toString()));
      return;
    } finally {
      try {
        Files.deleteIfExists(Paths.get(installerJar));
      } catch (Exception ignored) {
      }
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
        ErrorHelper.errorJson("Spigot BuildTools compilation failed. Ensure git and Java are properly installed.");
        return;
      }
    } catch (Exception e) {
      ErrorHelper.errorJson("Spigot compilation failed: " + (e.getMessage() != null ? e.getMessage() : e.toString()));
    }

    try (Stream<Path> jars = Files.list(Paths.get("."))) {
      jars.forEach((Path jar) -> {
        try {
          if (jar.getFileName().toString().startsWith("spigot-" + gVersion)) {
            Files.move(jar, Paths.get("server.jar"), StandardCopyOption.REPLACE_EXISTING);
          }
        } catch (Exception e) {
          ErrorHelper.errorJson("Could not rename compiled Spigot jar to server.jar: " + e.getMessage());
        }
      });

    } catch (Exception e) {
      ErrorHelper.errorJson("Could not locate compiled Spigot jar: " + e.getMessage());
    }

  }

  private void paper() {
    if (job == 3) {
      list.add("paper");
      return;
    }

    String url = "https://api.papermc.io/v3/projects/paper/versions/" + gVersion + "/builds/latest";
    HttpResponse<String> findingURL = NetworkUtils.attemptS(HttpRequest.newBuilder()
        .uri(URI.create(url))
        .GET()
        .build());

    if (findingURL == null || findingURL.body() == null) {
      ErrorHelper.errorJson("Failed to fetch Paper build metadata for version " + gVersion);
      return;
    }

    try {
      JsonObject jsonObj = JsonParser.parseString(findingURL.body()).getAsJsonObject();
      JsonObject downloads = jsonObj.getAsJsonObject("downloads");
      if (downloads == null || !downloads.has("server:default")) {
        ErrorHelper.errorJson("No server download found for Paper version " + gVersion);
        return;
      }
      JsonObject serverDefault = downloads.getAsJsonObject("server:default");
      String downloadUrl = serverDefault.get("url").getAsString();

      HttpRequest downloadRequest = HttpRequest.newBuilder()
          .uri(URI.create(downloadUrl))
          .GET()
          .build();

      HttpResponse<InputStream> downloading = NetworkUtils.attemptI(downloadRequest);
      if (downloading == null) {
        ErrorHelper.errorJson("Failed to download Paper server jar.");
        return;
      }

      long filesize = downloading.headers().firstValueAsLong("content-length").orElse(-1L);
      NetworkUtils.prog(downloading.body(), "server.jar", filesize);
    } catch (Exception e) {
      ErrorHelper.errorJson("Error downloading Paper: " + e.getMessage());
    }
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
      ErrorHelper.errorJson("tmux is not installed or not in PATH. Please install tmux (e.g. sudo apt install tmux).");
      return;
    }
    if (!checkCommand("java")) {
      ErrorHelper.errorJson("Java runtime not found in PATH. Please install Java (JDK 17/21).");
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
        "mcsv",
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
        ErrorHelper.errorJson("Could not write eula.txt: " + e.getMessage());
        return;
      }

      JsonObject response = new JsonObject();
      response.addProperty("status", 2);
      response.addProperty("type", "server");
      Communicator.printer(response);

      if (Shell.execute(command).waitFor() != 0) {
        ErrorHelper.errorJson("Minecraft server tmux session 'mcsv' is already running.");
      } else {
        JsonObject serverDone = new JsonObject();
        serverDone.addProperty("status", 3);
        serverDone.addProperty("type", "server");
        Communicator.printer(serverDone);
      }
    } catch (Exception e) {
      ErrorHelper.errorJson("Failed to launch Minecraft server session: " + (e.getMessage() != null ? e.getMessage() : e.toString()));
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
      ErrorHelper.errorJson("No Forge promotions found in remote metadata.");
      return;
    }

    String iVersion = null;
    if (promos.has(gVersion + "-recommended")) {
      iVersion = promos.get(gVersion + "-recommended").getAsString();
    } else if (promos.has(gVersion + "-latest")) {
      iVersion = promos.get(gVersion + "-latest").getAsString();
    } else {
      ErrorHelper.errorJson("No Forge version found for Minecraft " + gVersion);
      return;
    }

    String installerName = "forge-" + gVersion + "-" + iVersion + "-installer.jar";
    url = "https://maven.minecraftforge.net/net/minecraftforge/forge/" + gVersion + "-" + iVersion + "/forge-"
        + gVersion + "-" + iVersion + "-installer.jar";

    HttpResponse<InputStream> downloading = NetworkUtils
        .attemptI(HttpRequest.newBuilder().uri(URI.create(url)).GET().build());

    if (downloading == null) {
      ErrorHelper.errorJson("Failed to download Forge installer for version " + gVersion);
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
        ErrorHelper.errorJson("Forge installer execution failed. Please check Java version and network access.");
        return;
      }
    } catch (Exception e) {
      ErrorHelper.errorJson("Forge installer execution error: " + (e.getMessage() != null ? e.getMessage() : e.toString()));
    } finally {
      try {
        Files.deleteIfExists(Paths.get(installerName));
      } catch (Exception ignored) {
      }
    }

  }

  private void spawnServer(boolean x) {
    if (!checkCommand("tmux")) {
      ErrorHelper.errorJson("tmux is not installed or not in PATH. Please install tmux (e.g. sudo apt install tmux).");
      return;
    }
    if (!checkCommand("java")) {
      ErrorHelper.errorJson("Java runtime not found in PATH. Please install Java (JDK 17/21).");
      return;
    }

    JsonObject response = new JsonObject();
    response.addProperty("status", 2);
    response.addProperty("type", "server");
    Communicator.printer(response);

    try (FileWriter eulaWriter = new FileWriter("eula.txt")) {
      eulaWriter.write("eula=true");
    } catch (Exception e) {
      ErrorHelper.errorJson("Could not write eula.txt: " + e.getMessage());
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
      ErrorHelper.errorJson("Could not write Forge user_jvm_args.txt: " + e.getMessage());
    }

    try {
      String[] command = new String[] { "chmod", "+x", "run.sh" };
      if (Shell.execute(command).waitFor() != 0) {
        ErrorHelper.errorJson("Permission denied: Unable to make run.sh executable.");
        return;
      }

      command = new String[] { "tmux", "new-session", "-d", "-s", "mcsv", "./run.sh", "nogui" };

      if (Shell.execute(command).waitFor() != 0) {
        ErrorHelper.errorJson("Minecraft server tmux session 'mcsv' is already running.");
        return;
      }

    } catch (Exception e) {
      ErrorHelper.errorJson("Failed to launch Forge server session: " + (e.getMessage() != null ? e.getMessage() : e.toString()));
      return;
    }

    JsonObject serverDone = new JsonObject();
    serverDone.addProperty("status", 3);
    serverDone.addProperty("type", "server");
    Communicator.printer(serverDone);

  }
}
