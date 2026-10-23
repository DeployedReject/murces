
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
import java.nio.file.StandardOpenOption;
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
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import java.util.regex.Pattern;
import java.util.function.Consumer;

import org.codeberg.DeployedReject.utils.Platform;

public class ServerHandler {

  private static volatile Consumer<String> diagnosticLogger = System.err::println;

  public static void setDiagnosticLogger(Consumer<String> logger) {
    diagnosticLogger = logger != null ? logger : System.err::println;
  }

  private static void diagnostic(String message) {
    try {
      diagnosticLogger.accept(message);
    } catch (Exception e) {
      System.err.println(message);
    }
  }

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
  public boolean portableJdk = false;
  public String sessionName = "mcsv";
  public String serverDir = ".";
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
    return Platform.isCommandAvailable(cmd);
  }

  public static boolean isServerRunning() {
    return isServerRunning("mcsv");
  }

  public static boolean isServerRunning(String sessionName) {
    String sess = (sessionName != null && !sessionName.trim().isEmpty()) ? sessionName.trim() : "mcsv";
    String mux = Platform.getMultiplexer();
    try {
      Process p = new ProcessBuilder(mux, "has-session", "-t", sess).start();
      if (p.waitFor() == 0)
        return true;
      if ("mcsv".equals(sess)) {
        p = new ProcessBuilder(mux, "has-session", "-t", "mcServer").start();
        if (p.waitFor() == 0)
          return true;
        if (!Platform.isWindows()) {
          p = new ProcessBuilder("pgrep", "-f", "server.jar").start();
          if (p.waitFor() == 0)
            return true;
          p = new ProcessBuilder("pgrep", "-f", "fabric-server-launch.jar").start();
          return p.waitFor() == 0;
        }
      }
      return false;
    } catch (Exception e) {
      return false;
    }
  }

  public static ProcessResult startServer(boolean publicTunnel) {
    return startServer(publicTunnel, "4G", null, null, "mcsv", ".");
  }

  public static ProcessResult startServer(boolean publicTunnel, String ram) {
    return startServer(publicTunnel, ram, null, null, "mcsv", ".");
  }

  public static ProcessResult startServer(boolean publicTunnel, String ram, String javaBinOverride, String jdkHomeOverride) {
    return startServer(publicTunnel, ram, javaBinOverride, jdkHomeOverride, "mcsv", ".");
  }

  public static ProcessResult startServer(boolean publicTunnel, String ram, String javaBinOverride, String jdkHomeOverride, String sessionName, String workingDir) {
    String mux = Platform.getMultiplexer();
    if (!Platform.isCommandAvailable(mux)) {
      return new ProcessResult(1,
          "[ERROR] 'tmux' is not installed or not in PATH.\nMurces requires tmux to manage background Minecraft sessions.\nPlease install it (e.g. 'sudo apt install tmux' or 'pacman -S tmux').");
    }

    String sess = (sessionName != null && !sessionName.trim().isEmpty()) ? sessionName.trim() : "mcsv";
    File workDir = (workingDir != null && !workingDir.trim().isEmpty()) ? new File(workingDir) : new File(".");
    if (!workDir.exists()) {
      workDir.mkdirs();
    }

    String javaExec = javaBinOverride;
    String jdkHome = jdkHomeOverride;
    if (javaExec == null || javaExec.isEmpty() || !new File(javaExec).canExecute()) {
      javaExec = JdkManager.getJavaCommand(null, true);
    }

    if (!"java".equals(javaExec) && new File(javaExec).canExecute()) {
      if (jdkHome == null || jdkHome.isEmpty()) {
        File parent = new File(javaExec).getParentFile();
        if (parent != null) {
          jdkHome = parent.getParent();
        }
      }
    } else if (!Platform.isCommandAvailable("java")) {
      return new ProcessResult(1,
          "[ERROR] Neither 'java' in PATH nor an executable portable JDK was found.\nPlease install Java or enable Portable JDK in Murces.");
    } else {
      javaExec = "java";
    }

    ProcessResult existingSession = runLoggedCommand(Arrays.asList(mux, "has-session", "-t", sess), workDir, null);
    if (existingSession.exitCode == 0) {
      return new ProcessResult(1, "Minecraft server session '" + sess + "' is already running.");
    }

    File startupLog = new File(workDir, "murces-startup.log");
    try {
      Files.writeString(startupLog.toPath(), "=== MurCes server start: " + new java.util.Date() + " ===\n",
          StandardCharsets.UTF_8);
    } catch (Exception e) {
      diagnostic("[WARN] Could not initialize server startup log " + startupLog.getAbsolutePath() + ": " + e.getMessage());
    }

    File eulaFile = new File(workDir, "eula.txt");
    try (FileWriter eulaWriter = new FileWriter(eulaFile)) {
      eulaWriter.write("eula=true\n");
    } catch (Exception ignored) {
    }

    String ramArg = (ram != null && !ram.trim().isEmpty()) ? ram.trim() : "4G";
    if (ramArg.matches("^[0-9]+$")) {
      ramArg = ramArg + "G";
    }

    if (publicTunnel) {
      try {
        Tunnel.startDaemon();
      } catch (Exception ignored) {
      }
    }

    File userJvmArgs = new File(workDir, "user_jvm_args.txt");
    if (userJvmArgs.exists()) {
      try {
        String content = Files.readString(userJvmArgs.toPath(), StandardCharsets.UTF_8);
        content = content.replaceAll("-Xmx[0-9]*[GM]", "-Xmx" + ramArg)
            .replaceAll("-Xms[0-9]*[GM]", "-Xms" + ramArg);
        Files.writeString(userJvmArgs.toPath(), content, StandardCharsets.UTF_8);
      } catch (Exception ignored) {
      }
    }

    File runScript = findStartupScript(workDir);
    if (runScript == null && findPlatformServerArgs(workDir) == null && !hasKnownServerJar(workDir)) {
      File installerJar = findForgeInstaller(workDir);
      if (installerJar != null) {
        diagnostic("[INFO] Found Forge installer " + installerJar.getAbsolutePath()
            + "; running its headless --installServer setup before launching the server.");
        File installerTempDir = new File(workDir, ".murces-installer-tmp");
        try {
          Files.createDirectories(installerTempDir.toPath());
        } catch (IOException e) {
          return new ProcessResult(1, "Could not create Forge installer temporary directory "
              + installerTempDir.getAbsolutePath() + ": " + e.getMessage());
        }
        diagnostic("[INFO] Forge installer temporary files will use " + installerTempDir.getAbsolutePath());
        Map<String, String> environment = new HashMap<>();
        if (jdkHome != null && !jdkHome.isEmpty()) {
          String binPath = new File(jdkHome, "bin").getAbsolutePath();
          environment.put("JAVA_HOME", jdkHome);
          environment.put("PATH", binPath + File.pathSeparator + System.getenv("PATH"));
        }
        List<String> installerCommand = Arrays.asList(javaExec,
            "-Djava.io.tmpdir=" + installerTempDir.getAbsolutePath(),
            "-jar", installerJar.getName(), "--installServer");
        ProcessResult installResult = runLoggedCommand(installerCommand, workDir, environment);
        try {
          Files.writeString(startupLog.toPath(),
              installResult.output + System.lineSeparator() + "[MurCes] Forge installer exited with code "
                  + installResult.exitCode + System.lineSeparator(),
              StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        } catch (IOException e) {
          diagnostic("[WARN] Could not append Forge installer output to " + startupLog.getAbsolutePath() + ": " + e.getMessage());
        }
        if (installResult.exitCode != 0) {
          return new ProcessResult(installResult.exitCode,
              "Forge server installation failed (exit " + installResult.exitCode + ")."
                  + (installResult.output.isEmpty() ? "" : "\n" + installResult.output)
                  + "\nSee " + startupLog.getAbsolutePath());
        }
        runScript = findStartupScript(workDir);
      }
    }

    if (runScript != null && runScript.exists()) {
      try {
        if (!Platform.isWindows()) {
          ProcessResult chmodResult = runLoggedCommand(
              Arrays.asList("chmod", "+x", runScript.getAbsolutePath()), workDir, null);
          if (chmodResult.exitCode != 0) {
            diagnostic("[WARN] chmod failed, continuing with the script interpreter: " + chmodResult.output);
          }
        }
        List<String> scriptCmd = new ArrayList<>(Arrays.asList(mux, "new-session", "-d", "-s", sess));
        String scriptName = runScript.getName().toLowerCase(Locale.ROOT);
        if (Platform.isWindows()) {
          if (scriptName.endsWith(".sh")) {
            scriptCmd.addAll(Arrays.asList("sh", runScript.getName(), "nogui"));
          } else {
            scriptCmd.addAll(Arrays.asList("cmd.exe", "/c", runScript.getName(), "nogui"));
          }
        } else {
          String interpreter = "sh";
          if (scriptName.endsWith(".sh")) {
            try (BufferedReader reader = Files.newBufferedReader(runScript.toPath(), StandardCharsets.UTF_8)) {
              String shebang = reader.readLine();
              if (shebang != null && shebang.toLowerCase(Locale.ROOT).contains("bash")) {
                interpreter = "bash";
              }
            }
          }
          if (jdkHome != null && !jdkHome.isEmpty()) {
            String binPath = new File(jdkHome, "bin").getAbsolutePath();
            scriptCmd.addAll(Arrays.asList("env", "PATH=" + binPath + ":" + System.getenv("PATH"),
                "JAVA_HOME=" + jdkHome, interpreter, runScript.getName(), "nogui"));
          } else {
            scriptCmd.addAll(Arrays.asList(interpreter, runScript.getName(), "nogui"));
          }
        }
        Map<String, String> environment = new HashMap<>();
        if (jdkHome != null && !jdkHome.isEmpty()) {
          String binPath = new File(jdkHome, "bin").getAbsolutePath();
          environment.put("JAVA_HOME", jdkHome);
          environment.put("PATH", binPath + File.pathSeparator + System.getenv("PATH"));
        }
        List<String> launchCommand = new ArrayList<>(scriptCmd.subList(0, 5));
        List<String> actualCommand = scriptCmd.subList(5, scriptCmd.size());
        launchCommand.add(Platform.isWindows() ? String.join(" ", actualCommand)
            : loggedShellCommand(actualCommand, startupLog, environment));
        ProcessResult launchResult = runLoggedCommand(launchCommand, workDir, environment);
        if (launchResult.exitCode != 0) {
          return new ProcessResult(launchResult.exitCode, "Failed to start server session '" + sess + "'"
              + (launchResult.output.isEmpty() ? "." : ": " + launchResult.output));
        }
        for (int attempt = 0; attempt < 20; attempt++) {
          ProcessResult check = runLoggedCommand(Arrays.asList(mux, "has-session", "-t", sess), workDir, null);
          if (check.exitCode == 0) {
            return new ProcessResult(0, "Minecraft server started in " + mux + " session '" + sess + "' via " + runScript.getName() + ".");
          }
          Thread.sleep(100);
        }
        return startupFailure(sess, startupLog);
      } catch (Exception e) {
        return new ProcessResult(1, "Failed to launch " + runScript.getName() + ": " + e.getMessage());
      }
    }

    diagnostic("[INFO] No usable platform startup script found; resolving a server JAR or Java argument file.");

    // Determine target jar by scanning for known server launchers
    String targetJar = "server.jar";
    if (new File(workDir, "fabric-server-launch.jar").exists()) {
      targetJar = "fabric-server-launch.jar";
    } else if (new File(workDir, "fabric-server-launcher.jar").exists()) {
      targetJar = "fabric-server-launcher.jar";
    } else if (new File(workDir, "quilt-server-launch.jar").exists()) {
      targetJar = "quilt-server-launch.jar";
    } else if (new File(workDir, "server.jar").exists()) {
      targetJar = "server.jar";
    } else {
      // Find Forge universal/server jar or Paper/Spigot jar
      File[] jars = workDir.listFiles(f -> f.isFile() && f.getName().toLowerCase(Locale.ROOT).endsWith(".jar")
          && !f.getName().toLowerCase(Locale.ROOT).contains("installer"));
      if (jars != null) {
        // Priority 1: Forge server / universal jar (e.g. forge-1.7.10-10.13.4.1614-1.7.10-universal.jar)
        for (File j : jars) {
          String n = j.getName().toLowerCase();
          if (n.startsWith("forge-") && (n.contains("universal") || n.contains("server"))) {
            targetJar = j.getName();
            break;
          }
        }
        // Priority 2: Any forge jar
        if ("server.jar".equals(targetJar)) {
          for (File j : jars) {
            if (j.getName().toLowerCase().startsWith("forge-")) {
              targetJar = j.getName();
              break;
            }
          }
        }
        // Priority 3: Paper / Purpur / Spigot
        if ("server.jar".equals(targetJar)) {
          for (File j : jars) {
            String n = j.getName().toLowerCase();
            if (n.startsWith("paper") || n.startsWith("purpur") || n.startsWith("spigot")) {
              targetJar = j.getName();
              break;
            }
          }
        }
      }
    }

    File argsFile = findPlatformServerArgs(workDir);
    if (argsFile == null && (!new File(workDir, targetJar).isFile() || isInstallerJar(new File(workDir, targetJar)))) {
      return new ProcessResult(1,
          "No usable server startup script, Java server argument file, or server JAR was found in "
              + workDir.getAbsolutePath() + ". Forge installer JARs are not server launchers.");
    }
    if (argsFile != null) {
      diagnostic("[INFO] Using detected server Java arguments from " + argsFile.getAbsolutePath());
    }
    List<String> cmd = new ArrayList<>(Arrays.asList(mux, "new-session", "-d", "-s", sess, javaExec));
    if (argsFile == null) {
      cmd.addAll(Arrays.asList(
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
          "-jar", targetJar, "nogui"));
    } else {
      if (userJvmArgs.exists()) {
        cmd.add("@" + userJvmArgs.getName());
      } else {
        cmd.add("-Xmx" + ramArg);
        cmd.add("-Xms" + ramArg);
      }
      cmd.add("@" + workDir.toPath().toAbsolutePath().normalize()
          .relativize(argsFile.toPath().toAbsolutePath().normalize()));
      cmd.add("nogui");
    }

    try {
      Map<String, String> environment = new HashMap<>();
      if (jdkHome != null && !jdkHome.isEmpty()) {
        String binPath = new File(jdkHome, "bin").getAbsolutePath();
        environment.put("JAVA_HOME", jdkHome);
        environment.put("PATH", binPath + File.pathSeparator + System.getenv("PATH"));
      }
      List<String> launchCommand = new ArrayList<>(cmd.subList(0, 5));
      List<String> actualCommand = cmd.subList(5, cmd.size());
      launchCommand.add(Platform.isWindows() ? String.join(" ", actualCommand)
          : loggedShellCommand(actualCommand, startupLog, environment));
      ProcessResult launchResult = runLoggedCommand(launchCommand, workDir, environment);
      if (launchResult.exitCode != 0) {
        return new ProcessResult(launchResult.exitCode, "Failed to start server session '" + sess + "'"
            + (launchResult.output.isEmpty() ? "." : ": " + launchResult.output));
      }
      for (int attempt = 0; attempt < 20; attempt++) {
        ProcessResult check = runLoggedCommand(Arrays.asList(mux, "has-session", "-t", sess), workDir, null);
        if (check.exitCode == 0) {
          return new ProcessResult(0, "Minecraft server started in " + mux + " session '" + sess
              + (argsFile != null ? "' using " + argsFile.getName() + "." : "'."));
        }
        Thread.sleep(100);
      }
      return startupFailure(sess, startupLog);
    } catch (Exception e) {
      return new ProcessResult(1, "Failed to launch server session: " + e.getMessage());
    }
  }

  private static String loggedShellCommand(List<String> command, File logFile, Map<String, String> environment) {
    StringBuilder script = new StringBuilder();
    for (Map.Entry<String, String> variable : environment.entrySet()) {
      script.append("export ").append(variable.getKey()).append("=")
          .append(shellQuote(variable.getValue())).append("; ");
    }
    script.append("set +e; ").append(shellCommand(command))
        .append(" 2>&1 | tee -a ").append(shellQuote(logFile.getAbsolutePath()))
        .append("; status=${PIPESTATUS[0]}; printf '\\n[MurCes] Server process exited with code %s\\n' \"$status\"")
        .append(" | tee -a ").append(shellQuote(logFile.getAbsolutePath()))
        .append("; exit \"$status\"");
    return "bash -lc " + shellQuote(script.toString());
  }

  private static ProcessResult runLoggedCommand(List<String> command, File workingDirectory,
      Map<String, String> environment) {
    diagnostic("[CMD] " + shellCommand(command) + " (cwd=" + workingDirectory.getAbsolutePath() + ")");
    try {
      ProcessBuilder builder = new ProcessBuilder(command);
      builder.redirectErrorStream(true);
      builder.directory(workingDirectory);
      if (environment != null) builder.environment().putAll(environment);
      Process process = builder.start();
      process.getOutputStream().close();
      String output;
      try (InputStream stream = process.getInputStream()) {
        output = new String(stream.readAllBytes(), StandardCharsets.UTF_8).trim();
      }
      int exitCode = process.waitFor();
      diagnostic("[CMD] exit=" + exitCode + " for " + shellCommand(command)
          + (output.isEmpty() ? "" : " output: " + output));
      return new ProcessResult(exitCode, output);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      diagnostic("[ERR:] Command interrupted: " + shellCommand(command));
      return new ProcessResult(1, "Command interrupted.");
    } catch (IOException e) {
      diagnostic("[ERR:] Could not run command " + shellCommand(command) + ": " + e.getMessage());
      return new ProcessResult(1, e.getMessage());
    }
  }

  private static String shellCommand(List<String> command) {
    return command.stream().map(ServerHandler::shellQuote).collect(java.util.stream.Collectors.joining(" "));
  }

  private static String shellQuote(String value) {
    return "'" + value.replace("'", "'\\''") + "'";
  }

  private static ProcessResult startupFailure(String session, File startupLog) {
    String detail = "Server process exited during startup; tmux session '" + session + "' is no longer running.";
    try {
      if (startupLog.isFile()) {
        List<String> lines = Files.readAllLines(startupLog.toPath(), StandardCharsets.UTF_8);
        int start = Math.max(0, lines.size() - 60);
        String tail = String.join("\n", lines.subList(start, lines.size())).trim();
        if (!tail.isEmpty()) {
          detail += "\nStartup log (" + startupLog.getAbsolutePath() + "):\n" + tail;
          diagnostic("[ERR:] Server startup failed. Final output:\n" + tail);
        } else {
          detail += "\nNo output was captured. Startup log: " + startupLog.getAbsolutePath();
          diagnostic("[ERR:] Server exited without output. Startup log: " + startupLog.getAbsolutePath());
        }
      } else {
        detail += "\nStartup log was not created: " + startupLog.getAbsolutePath();
        diagnostic("[ERR:] Server exited before producing a startup log.");
      }
    } catch (IOException e) {
      detail += "\nCould not read startup log: " + e.getMessage();
      diagnostic("[ERR:] Could not read server startup log: " + e.getMessage());
    }
    return new ProcessResult(1, detail);
  }

  private static File findStartupScript(File workDir) {
    File[] candidates = workDir.listFiles(file -> {
      if (!file.isFile()) return false;
      String name = file.getName().toLowerCase(Locale.ROOT);
      return Platform.isWindows()
          ? name.endsWith(".bat") || name.endsWith(".cmd")
          : name.endsWith(".sh");
    });
    if (candidates == null) return null;
    return Arrays.stream(candidates)
        .sorted((left, right) -> {
          int rank = Integer.compare(startupScriptRank(left), startupScriptRank(right));
          return rank != 0 ? rank : left.getName().compareToIgnoreCase(right.getName());
        })
        .filter(ServerHandler::isServerStartupScript)
        .findFirst()
        .orElse(null);
  }

  private static boolean hasKnownServerJar(File workDir) {
    if (new File(workDir, "server.jar").isFile()
        || new File(workDir, "fabric-server-launch.jar").isFile()
        || new File(workDir, "fabric-server-launcher.jar").isFile()
        || new File(workDir, "quilt-server-launch.jar").isFile()) {
      return true;
    }
    File[] jars = workDir.listFiles(file -> file.isFile()
        && file.getName().toLowerCase(Locale.ROOT).endsWith(".jar")
        && !isInstallerJar(file));
    if (jars == null) return false;
    return Arrays.stream(jars).anyMatch(file -> {
      String name = file.getName().toLowerCase(Locale.ROOT);
      return name.startsWith("forge-") || name.startsWith("neoforge-")
          || name.startsWith("paper") || name.startsWith("purpur")
          || name.startsWith("spigot") || name.startsWith("craftbukkit");
    });
  }

  private static File findForgeInstaller(File workDir) {
    File[] jars = workDir.listFiles(file -> file.isFile() && isInstallerJar(file));
    if (jars == null) return null;
    return Arrays.stream(jars)
        .filter(file -> {
          String name = file.getName().toLowerCase(Locale.ROOT);
          return name.startsWith("forge-") || name.startsWith("neoforge-") || name.equals("installer.jar");
        })
        .sorted(Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER))
        .findFirst()
        .orElse(null);
  }

  private static boolean isInstallerJar(File jar) {
    return jar.getName().toLowerCase(Locale.ROOT).contains("installer")
        && jar.getName().toLowerCase(Locale.ROOT).endsWith(".jar");
  }

  private static boolean isServerStartupScript(File script) {
    try {
      String contents = Files.readString(script.toPath(), StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
      boolean launchesInstaller = contents.contains("net.minecraftforge.installer.simpleinstaller")
          || Pattern.compile("(?i)(?:java|javaw)[^\\r\\n]*-jar[^\\r\\n]*\\S*installer[^\\r\\n]*\\.jar")
              .matcher(contents).find();
      if (launchesInstaller) {
        diagnostic("[WARN] Skipping " + script.getName()
            + ": it launches the Forge installer, not the server. Looking for server arguments or a server JAR instead.");
        return false;
      }
      return true;
    } catch (IOException e) {
      diagnostic("[WARN] Could not inspect startup script " + script.getAbsolutePath() + ": " + e.getMessage());
      return false;
    }
  }

  private static int startupScriptRank(File script) {
    String name = script.getName().toLowerCase(Locale.ROOT);
    int rank = 10;
    if (name.startsWith("run")) rank = 0;
    else if (name.contains("startserver")) rank = 1;
    else if (name.startsWith("start")) rank = 2;
    else if (name.contains("launch")) rank = 3;
    if (name.contains("server")) rank--;
    return rank;
  }

  private static File findPlatformServerArgs(File workDir) {
    Path root = workDir.toPath().toAbsolutePath().normalize();
    try (Stream<Path> files = Files.walk(root, 10)) {
      return files.filter(Files::isRegularFile)
          .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".txt"))
          .map(path -> new AbstractMap.SimpleEntry<>(path, scoreServerArgumentFile(path, root)))
          .filter(candidate -> candidate.getValue() >= 6)
          .sorted((left, right) -> {
            int score = Integer.compare(right.getValue(), left.getValue());
            return score != 0 ? score : left.getKey().compareTo(right.getKey());
          })
          .map(candidate -> candidate.getKey().toFile())
          .findFirst()
          .orElse(null);
    } catch (Exception e) {
      System.err.println("[WARN] Could not search for platform-specific server arguments: " + e.getMessage());
    }
    return null;
  }

  private static int scoreServerArgumentFile(Path candidate, Path root) {
    String filename = candidate.getFileName().toString().toLowerCase(Locale.ROOT);
    if (filename.equals("user_jvm_args.txt") || filename.equals("eula.txt")) return -1;
    try {
      long size = Files.size(candidate);
      if (size == 0 || size > 1024 * 1024) return -1;
      String contents = Files.readString(candidate, StandardCharsets.UTF_8);
      String normalized = contents.toLowerCase(Locale.ROOT);
      if (normalized.contains("net.minecraftforge.installer.simpleinstaller")
          || normalized.contains("net.minecraftforge.installer.installerpanel")
          || (normalized.contains("installer") && normalized.contains(".jar"))) {
        diagnostic("[WARN] Ignoring installer arguments in " + candidate
            + "; these launch the graphical Forge installer, not the server.");
        return -1;
      }
      boolean hasLaunchArgs = matchesArgumentLine(normalized,
          "(?:^|\\s)(?:-cp|-classpath|--class-path|--module-path|-p|-jar)\\s+")
          || normalized.matches("(?s).*(?:net\\.minecraft\\.server\\.main|net\\.minecraftforge\\.|net\\.neoforged\\.|cpw\\.mods\\.|bootstraplauncher).*");
      if (!hasLaunchArgs) return -1;

      int score = 0;
      if (matchesArgumentLine(normalized, "(?:^|\\s)(?:-cp|-classpath|--class-path|--module-path|-p)\\s+")) score += 8;
      if (matchesArgumentLine(normalized, "(?:^|\\s)-jar\\s+\\S+\\.jar(?:\\s|$)")) score += 8;
      if (normalized.matches("(?s).*(?:net\\.minecraft\\.server\\.main|net\\.minecraftforge\\.|net\\.neoforged\\.|cpw\\.mods\\.|bootstraplauncher).*")) score += 8;
      if (matchesArgumentLine(normalized, "^\\s*--launchtarget\\s+\\S+")) score += 3;
      if (matchesArgumentLine(normalized, "^\\s*--fml\\.[\\w.-]+")) score += 2;
      if (filename.contains("args") || filename.contains("java")) score += 2;

      String lowerPath = root.relativize(candidate).toString().toLowerCase(Locale.ROOT);
      if (lowerPath.contains("libraries") && (lowerPath.contains("minecraftforge") || lowerPath.contains("neoforged"))) {
        score += 4;
      }
      if (Platform.isWindows()) {
        if (filename.contains("win") || filename.contains("windows")) score += 2;
        if (filename.contains("unix") || filename.contains("linux")) score -= 8;
      } else {
        if (filename.contains("unix") || filename.contains("linux")) score += 2;
        if (filename.contains("win") || filename.contains("windows")) score -= 8;
      }
      return score;
    } catch (Exception e) {
      System.err.println("[WARN] Could not inspect server argument file " + candidate + ": " + e.getMessage());
      return -1;
    }
  }

  private static boolean matchesArgumentLine(String contents, String regex) {
    return Pattern.compile(regex, Pattern.MULTILINE).matcher(contents).find();
  }

  public static ProcessResult stopServer() {
    return stopServer("mcsv");
  }

  public static ProcessResult stopServer(String sessionName) {
    String mux = Platform.getMultiplexer();
    if (!Platform.isCommandAvailable(mux)) {
      return new ProcessResult(1, "[ERROR] '" + mux + "' is not installed or not in PATH.");
    }

    String sess = (sessionName != null && !sessionName.trim().isEmpty()) ? sessionName.trim() : "mcsv";

    boolean hasSession = false;
    try {
      Process p = new ProcessBuilder(mux, "has-session", "-t", sess).start();
      hasSession = (p.waitFor() == 0);
    } catch (Exception ignored) {
    }

    if (!hasSession) {
      return new ProcessResult(0, "Minecraft server session '" + sess + "' is not running.");
    }

    try {
      new ProcessBuilder(mux, "send-keys", "-t", sess, "stop", "C-m").start().waitFor();

      for (int i = 0; i < 30; i++) {
        Thread.sleep(500);
        Process p = new ProcessBuilder(mux, "has-session", "-t", sess).start();
        if (p.waitFor() != 0) {
          return new ProcessResult(0, "Minecraft server stopped.");
        }
      }

      new ProcessBuilder(mux, "kill-session", "-t", sess).start().waitFor();
      return new ProcessResult(0, "Minecraft server session terminated.");
    } catch (Exception e) {
      return new ProcessResult(1, "Failed to stop server: " + e.getMessage());
    }
  }

  public static ProcessResult sendConsoleCommand(String cmd) {
    return sendConsoleCommand("mcsv", cmd);
  }

  public static ProcessResult sendConsoleCommand(String sessionName, String cmd) {
    String mux = Platform.getMultiplexer();
    if (!Platform.isCommandAvailable(mux)) {
      return new ProcessResult(1, "[ERROR] '" + mux + "' is not installed or not in PATH.");
    }
    String sess = (sessionName != null && !sessionName.trim().isEmpty()) ? sessionName.trim() : "mcsv";
    try {
      Process p = new ProcessBuilder(mux, "has-session", "-t", sess).start();
      if (p.waitFor() != 0) {
        return new ProcessResult(1, "Minecraft server session '" + sess + "' not found.");
      }
      new ProcessBuilder(mux, "send-keys", "-t", sess, cmd, "C-m").start().waitFor();
      return new ProcessResult(0, "Command dispatched to Minecraft server.");
    } catch (Exception e) {
      return new ProcessResult(1, "Failed to send command: " + e.getMessage());
    }
  }

  public void serverHandler() {

    if (job == 1 || job == 0) {
      if (portableJdk) {
        int reqJdk = JdkManager.getRequiredJdkVersion(gVersion);
        if (!JdkManager.isJdkInstalled(reqJdk)) {
          JsonObject startJdk = new JsonObject();
          startJdk.addProperty("status", 2);
          startJdk.addProperty("type", "jdk");
          startJdk.addProperty("message", "Downloading Portable JDK " + reqJdk + " for Minecraft " + gVersion + "...");
          Communicator.printer(startJdk);
          boolean ok = JdkManager.downloadAndExtractJdk(reqJdk, null, null);
          if (!ok) {
            ErrorHelper.errorJson("Failed to download Portable JDK " + reqJdk + " for Minecraft " + gVersion);
            return;
          }
        }
      }

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
      case 1:
        File sDir = new File(serverDir != null ? serverDir : ".");
        File eulaFile = new File(sDir, "eula.txt");
        try (FileWriter eulaWriter = new FileWriter(eulaFile)) {
          eulaWriter.write("eula=true\n");
        } catch (Exception ignored) {
        }
        try {
          org.codeberg.DeployedReject.utils.ServerJarMetadata.recordServerMetadata(sDir, loader, gVersion, lVersion);
        } catch (Exception ignored) {
        }
        JsonObject comp = new JsonObject();
        comp.addProperty("status", 3);
        comp.addProperty("type", "server");
        Communicator.printer(comp);
        if (job == 1) {
          startServer(false, String.valueOf(ram) + "G", null, null, sessionName, serverDir);
        }
        break;
      case 2:
        stopServer(sessionName);
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
        statusResp.addProperty("running", isServerRunning(sessionName));
        Communicator.printer(statusResp);
        break;
      case 5:
        startServer(false, String.valueOf(ram) + "G", null, null, sessionName, serverDir);
        break;
      case 6:
        stopServer(sessionName);
        try {
          Thread.sleep(1000);
        } catch (Exception ignored) {
        }
        startServer(false, String.valueOf(ram) + "G", null, null, sessionName, serverDir);
        break;
      case 7:
        ProcessResult cmdRes = sendConsoleCommand(sessionName, command != null ? command : "");
        JsonObject cmdResp = new JsonObject();
        cmdResp.addProperty("status", cmdRes.exitCode == 0 ? 0 : 1);
        cmdResp.addProperty("type", "server");
        cmdResp.addProperty("output", cmdRes.output);
        Communicator.printer(cmdResp);
        break;
      case 8:
        ProcessResult migRes = MigrationHandler.migratePlayer(oldName, newName, sessionName, serverDir);
        JsonObject migResp = new JsonObject();
        migResp.addProperty("status", migRes.exitCode == 0 ? 0 : 1);
        migResp.addProperty("type", "server");
        migResp.addProperty("output", migRes.output);
        Communicator.printer(migResp);
        break;
      case 9:
        ProcessResult bakRes = BackupHandler.runBackup(sourceFolder, targetFolder, retentionLimit, cloudSync, cloudRemote, sessionName, serverDir);
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

    String javaExec = JdkManager.getJavaCommand(gVersion, portableJdk);
    if ("java".equals(javaExec) && !checkCommand("java")) {
      ErrorHelper.errorJson("Java runtime not found in PATH. Please install Java (JDK 17/21) or enable Portable JDK.");
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
    commandList.add(javaExec);
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

    String javaExec = JdkManager.getJavaCommand(gVersion, portableJdk);
    if ("java".equals(javaExec) && !checkCommand("java")) {
      ErrorHelper.errorJson("Java runtime not found in PATH. Please install Java (JDK 17/21) or enable Portable JDK.");
      return;
    }

    String[] command = new String[] {
        javaExec,
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
    return Platform.isCommandAvailable(cmd);
  }

  private void spawnServer() {
    String mux = Platform.getMultiplexer();
    if (!checkCommand(mux)) {
      ErrorHelper.errorJson("tmux is not installed or not in PATH. Please install tmux (e.g. sudo apt install tmux).");
      return;
    }
    String javaExec = JdkManager.getJavaCommand(gVersion, portableJdk);
    if ("java".equals(javaExec) && !checkCommand("java")) {
      ErrorHelper.errorJson("Java runtime not found in PATH. Please install Java (JDK 17/21) or enable Portable JDK.");
      return;
    }

    String targetJar = "server.jar";
    if ("fabric".equalsIgnoreCase(loader) || Files.exists(Paths.get("fabric-server-launch.jar"))) {
      targetJar = "fabric-server-launch.jar";
    }

    String[] command = new String[] {
        mux,
        "new-session",
        "-d",
        "-s",
        "mcsv",
        javaExec,
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
        ErrorHelper.errorJson("Minecraft server " + mux + " session 'mcsv' is already running.");
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

    String javaExec = JdkManager.getJavaCommand(gVersion, portableJdk);
    if ("java".equals(javaExec) && !checkCommand("java")) {
      ErrorHelper.errorJson("Java runtime not found in PATH. Please install Java (JDK 17/21) or enable Portable JDK.");
      return;
    }

    String[] command = new String[] { javaExec, "-jar", installerName,
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
    String mux = Platform.getMultiplexer();
    if (!checkCommand(mux)) {
      ErrorHelper.errorJson("tmux is not installed or not in PATH. Please install tmux (e.g. sudo apt install tmux).");
      return;
    }
    String javaExec = JdkManager.getJavaCommand(gVersion, portableJdk);
    if ("java".equals(javaExec) && !checkCommand("java")) {
      ErrorHelper.errorJson("Java runtime not found in PATH. Please install Java (JDK 17/21) or enable Portable JDK.");
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
      File runScript = Platform.isWindows()
          ? (new File("run.bat").exists() ? new File("run.bat") : (new File("run.cmd").exists() ? new File("run.cmd") : new File("run.sh")))
          : new File("run.sh");

      if (!Platform.isWindows() && runScript.exists()) {
        String[] chmodCmd = new String[] { "chmod", "+x", runScript.getName() };
        if (Shell.execute(chmodCmd).waitFor() != 0) {
          ErrorHelper.errorJson("Permission denied: Unable to make " + runScript.getName() + " executable.");
          return;
        }
      }

      String[] command;
      if (Platform.isWindows()) {
        if (runScript.getName().endsWith(".sh")) {
          command = new String[] { mux, "new-session", "-d", "-s", "mcsv", "sh", runScript.getName(), "nogui" };
        } else {
          command = new String[] { mux, "new-session", "-d", "-s", "mcsv", "cmd.exe", "/c", runScript.getName(), "nogui" };
        }
      } else {
        command = new String[] { mux, "new-session", "-d", "-s", "mcsv", "./" + runScript.getName(), "nogui" };
      }

      if (Shell.execute(command).waitFor() != 0) {
        ErrorHelper.errorJson("Minecraft server " + mux + " session 'mcsv' is already running.");
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
