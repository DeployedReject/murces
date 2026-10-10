package org.codeberg.DeployedReject.tui.backend;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.codeberg.DeployedReject.device.BackupHandler;
import org.codeberg.DeployedReject.device.MigrationHandler;
import org.codeberg.DeployedReject.device.ServerHandler;
import org.codeberg.DeployedReject.mods.CurseForge;
import org.codeberg.DeployedReject.mods.Modrinth;
import org.codeberg.DeployedReject.tui.config.ConfigManager;
import org.codeberg.DeployedReject.tui.config.TuiConfig;
import org.codeberg.DeployedReject.utils.Communicator;
import org.codeberg.DeployedReject.utils.NetworkUtils;
import org.codeberg.DeployedReject.utils.Platform;

import java.io.*;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

public class OrchestratorBridge {

  private static OrchestratorBridge instance;

  public static synchronized OrchestratorBridge getInstance() {
    if (instance == null) {
      instance = new OrchestratorBridge();
    }
    return instance;
  }

  public static class ModResult {
    public final String id;
    public final String name;
    public final String author;
    public final String description;

    public ModResult(String id, String name) {
      this(id, name, "", "");
    }

    public ModResult(String id, String name, String author, String description) {
      this.id = id;
      this.name = name;
      this.author = author != null ? author : "";
      this.description = description != null ? description : "";
    }

    @Override
    public String toString() {
      if (author != null && !author.isEmpty()) {
        return name + " by " + author;
      }
      return name + " (" + id + ")";
    }
  }

  public static class ModVersionInfo {
    public final String versionId;
    public final String versionName;
    public final String versionNumber;
    public final String downloadUrl;
    public final String filename;
    public final long sizeBytes;

    public ModVersionInfo(String versionId, String versionName, String versionNumber, String downloadUrl,
        String filename, long sizeBytes) {
      this.versionId = versionId;
      this.versionName = versionName;
      this.versionNumber = versionNumber;
      this.downloadUrl = downloadUrl;
      this.filename = filename;
      this.sizeBytes = sizeBytes;
    }

    @Override
    public String toString() {
      if (versionNumber != null && !versionNumber.isEmpty()) {
        return versionNumber;
      }
      if (versionName != null && !versionName.isEmpty()) {
        return versionName;
      }
      return versionId;
    }
  }

  public static class ModpackResult {
    public final String id;
    public final String slug;
    public final String name;
    public final String author;
    public final String description;
    public final int downloads;

    public ModpackResult(String id, String slug, String name, String author, String description, int downloads) {
      this.id = id != null ? id : "";
      this.slug = slug != null ? slug : "";
      this.name = name != null ? name : "";
      this.author = author != null ? author : "";
      this.description = description != null ? description : "";
      this.downloads = downloads;
    }

    @Override
    public String toString() {
      if (author != null && !author.isEmpty()) {
        return name + " by " + author;
      }
      return name + " (" + slug + ")";
    }
  }

  public static class ModpackVersionInfo {
    public final String versionId;
    public final String versionName;
    public final String versionNumber;
    public final String mrpackUrl;
    public final String filename;
    public final long sizeBytes;
    public final int dependencyCount;

    public ModpackVersionInfo(String versionId, String versionName, String versionNumber, String mrpackUrl,
        String filename, long sizeBytes, int dependencyCount) {
      this.versionId = versionId;
      this.versionName = versionName;
      this.versionNumber = versionNumber;
      this.mrpackUrl = mrpackUrl;
      this.filename = filename;
      this.sizeBytes = sizeBytes;
      this.dependencyCount = dependencyCount;
    }

    @Override
    public String toString() {
      if (versionNumber != null && !versionNumber.isEmpty()) {
        return versionNumber + (dependencyCount > 0 ? " (" + dependencyCount + " mods)" : "");
      }
      if (versionName != null && !versionName.isEmpty()) {
        return versionName + (dependencyCount > 0 ? " (" + dependencyCount + " mods)" : "");
      }
      return versionId;
    }
  }

  public static class ModpackDependencyInfo {
    public final String projectId;
    public final String versionId;
    public final String name;
    public final String filename;
    public final String downloadUrl;
    public final long sizeBytes;
    public final String environment;
    public final String dependencyType;

    public ModpackDependencyInfo(String projectId, String versionId, String name, String filename,
        String downloadUrl, long sizeBytes, String environment, String dependencyType) {
      this.projectId = projectId != null ? projectId : "";
      this.versionId = versionId != null ? versionId : "";
      this.name = name != null ? name : "";
      this.filename = filename != null ? filename : "";
      this.downloadUrl = downloadUrl != null ? downloadUrl : "";
      this.sizeBytes = sizeBytes;
      this.environment = environment != null ? environment : "";
      this.dependencyType = dependencyType != null ? dependencyType : "required";
    }

    public boolean isClientOnly() {
      return "client_only".equalsIgnoreCase(environment) || "client".equalsIgnoreCase(environment);
    }

    public boolean isServerOnly() {
      return "server_only".equalsIgnoreCase(environment) || "server".equalsIgnoreCase(environment);
    }

    public String formattedEnvironment() {
      if (isClientOnly()) return "Client Only";
      if (isServerOnly()) return "Server Only";
      return "Server & Client";
    }
  }

  public static class ModpackInstallSummary {
    public final String modpackName;
    public final int totalDependencies;
    public final int installedCount;
    public final int skippedClientCount;
    public final int errorCount;
    public final long totalBytes;
    public final List<String> installedModNames;

    public ModpackInstallSummary(String modpackName, int totalDependencies, int installedCount,
        int skippedClientCount, int errorCount, long totalBytes, List<String> installedModNames) {
      this.modpackName = modpackName;
      this.totalDependencies = totalDependencies;
      this.installedCount = installedCount;
      this.skippedClientCount = skippedClientCount;
      this.errorCount = errorCount;
      this.totalBytes = totalBytes;
      this.installedModNames = installedModNames != null ? installedModNames : Collections.emptyList();
    }

    public String formattedSummary() {
      return String.format("Modpack '%s' installed successfully: %d mods downloaded (%s), %d client-only mods skipped, %d errors.",
          modpackName, installedCount, formatBytes(totalBytes), skippedClientCount, errorCount);
    }

    private static String formatBytes(long bytes) {
      if (bytes < 1024) return bytes + " B";
      int exp = (int) (Math.log(bytes) / Math.log(1024));
      char unit = "KMGTPE".charAt(exp - 1);
      return String.format("%.1f %cB", bytes / Math.pow(1024, exp), unit);
    }
  }

  public static class DownloadProgressInfo {
    public final double percent;
    public final long bytesRead;
    public final long totalBytes;
    public final double speedMBps;
    public final int etaSeconds;

    public DownloadProgressInfo(double percent, long bytesRead, long totalBytes, double speedMBps, int etaSeconds) {
      this.percent = percent;
      this.bytesRead = bytesRead;
      this.totalBytes = totalBytes;
      this.speedMBps = speedMBps;
      this.etaSeconds = etaSeconds;
    }

    public String formattedSpeed() {
      return String.format("%.2f MB/s", speedMBps);
    }

    public String formattedEta() {
      if (etaSeconds <= 0)
        return "calculating...";
      if (etaSeconds < 60)
        return etaSeconds + "s";
      return (etaSeconds / 60) + "m " + (etaSeconds % 60) + "s";
    }
  }

  public static class PluginResult {
    public final String id;
    public final String slug;
    public final String name;
    public final String author;
    public final String description;
    public final int downloads;
    public final List<String> categories;

    public PluginResult(String id, String slug, String name, String author, String description, int downloads, List<String> categories) {
      this.id = id != null ? id : "";
      this.slug = slug != null ? slug : "";
      this.name = name != null ? name : "";
      this.author = author != null ? author : "";
      this.description = description != null ? description : "";
      this.downloads = downloads;
      this.categories = categories != null ? categories : Collections.emptyList();
    }

    @Override
    public String toString() {
      if (author != null && !author.isEmpty()) {
        return name + " by " + author;
      }
      return name + " (" + slug + ")";
    }
  }

  public static class PluginVersionInfo {
    public final String versionId;
    public final String versionName;
    public final String versionNumber;
    public final String downloadUrl;
    public final String filename;
    public final long sizeBytes;

    public PluginVersionInfo(String versionId, String versionName, String versionNumber, String downloadUrl,
        String filename, long sizeBytes) {
      this.versionId = versionId != null ? versionId : "";
      this.versionName = versionName != null ? versionName : "";
      this.versionNumber = versionNumber != null ? versionNumber : "";
      this.downloadUrl = downloadUrl != null ? downloadUrl : "";
      this.filename = filename != null ? filename : "";
      this.sizeBytes = sizeBytes;
    }

    @Override
    public String toString() {
      if (versionNumber != null && !versionNumber.isEmpty()) {
        return versionNumber;
      }
      if (versionName != null && !versionName.isEmpty()) {
        return versionName;
      }
      return versionId;
    }
  }

  public static class PluginInstallSummary {
    public final String pluginName;
    public final String version;
    public final String filename;
    public final long sizeBytes;

    public PluginInstallSummary(String pluginName, String version, String filename, long sizeBytes) {
      this.pluginName = pluginName;
      this.version = version;
      this.filename = filename;
      this.sizeBytes = sizeBytes;
    }

    public String formattedSummary() {
      return String.format("Plugin '%s' (%s) installed to plugins/%s (%s)", pluginName, version, filename, formatBytes(sizeBytes));
    }

    private static String formatBytes(long bytes) {
      if (bytes < 1024) return bytes + " B";
      int exp = (int) (Math.log(bytes) / Math.log(1024));
      char unit = "KMGTPE".charAt(exp - 1);
      return String.format("%.1f %cB", bytes / Math.pow(1024, exp), unit);
    }
  }

  public static class PluginFileInfo {
    public final String filename;
    public final String name;
    public final String version;
    public final String author;
    public final String description;
    public final long sizeBytes;
    public final long lastModified;

    public PluginFileInfo(String filename, String name, String version, String author, String description, long sizeBytes, long lastModified) {
      this.filename = filename;
      this.name = name != null && !name.isEmpty() ? name : filename;
      this.version = version != null ? version : "";
      this.author = author != null ? author : "";
      this.description = description != null ? description : "";
      this.sizeBytes = sizeBytes;
      this.lastModified = lastModified;
    }

    public String getDisplayName() {
      if (!version.isEmpty()) {
        return name + " (v" + version + ")";
      }
      return name;
    }
  }

  public static class BackupInfo {
    public final String name;
    public final long sizeBytes;
    public final long lastModified;

    public BackupInfo(String name, long sizeBytes, long lastModified) {
      this.name = name;
      this.sizeBytes = sizeBytes;
      this.lastModified = lastModified;
    }

    public String formattedSize() {
      if (sizeBytes < 1024)
        return sizeBytes + " B";
      int exp = (int) (Math.log(sizeBytes) / Math.log(1024));
      char unit = "KMGTPE".charAt(exp - 1);
      return String.format("%.1f %cB", sizeBytes / Math.pow(1024, exp), unit);
    }

    public String formattedDate() {
      SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
      return sdf.format(new Date(lastModified));
    }
  }

  public static class TunnelInfo {
    public final String id;
    public final String proto;
    public final String publicAddress;
    public final int localPort;

    public TunnelInfo(String id, String proto, String publicAddress, int localPort) {
      this.id = id != null ? id : "";
      this.proto = proto != null ? proto : "tcp";
      this.publicAddress = publicAddress != null ? publicAddress : "";
      this.localPort = localPort;
    }
  }

  public static class ProcessResult {
    public final int exitCode;
    public final String output;

    public ProcessResult(int exitCode, String output) {
      this.exitCode = exitCode;
      this.output = output;
    }
  }

  private final CopyOnWriteArrayList<Consumer<JsonObject>> listeners = new CopyOnWriteArrayList<>();
  private final CopyOnWriteArrayList<Consumer<String>> logListeners = new CopyOnWriteArrayList<>();
  private final ExecutorService workerPool = Executors.newCachedThreadPool(r -> {
    Thread t = new Thread(r, "OrchestratorWorker");
    t.setDaemon(true);
    return t;
  });
  private String curseAPI;
  private String email = "user@murces.local";

  private OrchestratorBridge() {
    try {
      if (!Files.isDirectory(Paths.get("mods"))) {
        Files.createDirectories(Paths.get("mods"));
      }
    } catch (Exception ignored) {
    }

    loadConfig();
    startQueueConsumer();
  }

  private void loadConfig() {

    Properties env = new Properties();
    try (InputStream envStream = getClass().getClassLoader().getResourceAsStream("config.properties")) {
      if (envStream != null) {
        env.load(envStream);
        String propCurse = env.getProperty("curseAPI");
        if (propCurse != null && !propCurse.startsWith("${") && !propCurse.isEmpty()) {
          curseAPI = propCurse;
        }
        String propEmail = env.getProperty("email");
        if (propEmail != null && !propEmail.startsWith("${") && !propEmail.isEmpty()) {
          email = propEmail;
        }
      }
    } catch (Exception ignored) {
    }

    File[] envCandidates = new File[] {
        new File(".env"),
        new File("../.env"),
        new File(System.getProperty("user.dir", "."), ".env")
    };
    for (File f : envCandidates) {
      if (f.exists() && f.isFile()) {
        try (BufferedReader reader = new BufferedReader(new FileReader(f, StandardCharsets.UTF_8))) {
          String line;
          while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#"))
              continue;
            int eq = line.indexOf('=');
            if (eq > 0) {
              String key = line.substring(0, eq).trim();
              String val = line.substring(eq + 1).trim();
              if ((val.startsWith("\"") && val.endsWith("\"")) ||
                  (val.startsWith("'") && val.endsWith("'"))) {
                val = val.substring(1, val.length() - 1);
              }
              if ("curseAPI".equalsIgnoreCase(key) && !val.isEmpty()) {
                curseAPI = val;
              } else if ("email".equalsIgnoreCase(key) && !val.isEmpty()) {
                email = val;
              }
            }
          }
        } catch (Exception ignored) {
        }
        break;
      }
    }

    if (System.getenv("curseAPI") != null && !System.getenv("curseAPI").isEmpty()) {
      curseAPI = System.getenv("curseAPI");
    }
    if (System.getenv("email") != null && !System.getenv("email").isEmpty()) {
      email = System.getenv("email");
    }

    if (email != null && (email.startsWith("${") || email.isEmpty())) {
      email = "user@murces.local";
    }
  }

  private void startQueueConsumer() {
    Thread consumer = new Thread(() -> {
      while (!Thread.currentThread().isInterrupted()) {
        try {
          JsonObject json = Communicator.printBuffer.take();
          if (json.has("error")) {
            log("[ERR:] " + json.get("error").getAsString());
          }
          if (json.has("type") && "tunnel".equals(json.get("type").getAsString())) {
            String action = json.has("action") ? json.get("action").getAsString() : "";
            if (json.has("url")) {
              log("[TUNNEL] Claim Agent URL: " + json.get("url").getAsString());
            } else if (json.has("message")) {
              log("[TUNNEL] " + json.get("message").getAsString());
            } else if ("status".equals(action)) {
              boolean linked = json.has("linked") && json.get("linked").getAsBoolean();
              if (linked && json.has("tunnels")) {
                JsonArray arr = json.getAsJsonArray("tunnels");
                if (arr.size() > 0) {
                  for (JsonElement el : arr) {
                    if (el.isJsonObject()) {
                      JsonObject tObj = el.getAsJsonObject();
                      String pub = tObj.has("publicAddress") ? tObj.get("publicAddress").getAsString() : "";
                      int port = tObj.has("localPort") ? tObj.get("localPort").getAsInt() : 25565;
                      log("[TUNNEL] Route: " + pub + " -> localhost:" + port);
                    }
                  }
                } else {
                  log("[TUNNEL] Agent is linked, but no tunnels active.");
                }
              } else {
                log("[TUNNEL] Agent is not linked.");
              }
            }
          }
          for (Consumer<JsonObject> l : listeners) {
            try {
              l.accept(json);
            } catch (Exception ignored) {
            }
          }
        } catch (InterruptedException e) {
          break;
        }
      }
    }, "OrchestratorQueueConsumer");
    consumer.setDaemon(true);
    consumer.start();
    log("[OK:] In-memory Orchestrator initialized.");
  }

  public void addListener(Consumer<JsonObject> listener) {
    listeners.add(listener);
  }

  public void removeListener(Consumer<JsonObject> listener) {
    listeners.remove(listener);
  }

  public void addLogListener(Consumer<String> listener) {
    logListeners.add(listener);
  }

  public void log(String msg) {
    for (Consumer<String> l : logListeners) {
      try {
        l.accept(msg);
      } catch (Exception ignored) {
      }
    }
  }

  public void stopOrchestrator() {
    workerPool.shutdownNow();
  }

  public CompletableFuture<List<ModResult>> searchMods(String platform, String query, String version, String loader) {
    CompletableFuture<List<ModResult>> future = new CompletableFuture<>();

    Consumer<JsonObject> handler = new Consumer<>() {
      @Override
      public void accept(JsonObject json) {
        if (json.has("type") && "query".equals(json.get("type").getAsString()) && json.has("mods")) {
          List<ModResult> results = new ArrayList<>();
          JsonArray mods = json.getAsJsonArray("mods");
          for (JsonElement el : mods) {
            if (el.isJsonArray()) {
              JsonArray pair = el.getAsJsonArray();
              if (pair.size() >= 2) {
                String id = pair.get(0).getAsString();
                String name = pair.get(1).getAsString();
                String author = pair.size() >= 3 ? pair.get(2).getAsString() : "";
                String description = pair.size() >= 4 ? pair.get(3).getAsString() : "";
                results.add(new ModResult(id, name, author, description));
              }
            }
          }
          removeListener(this);
          future.complete(results);
        } else if (json.has("error")) {
          removeListener(this);
          future.completeExceptionally(new RuntimeException(json.get("error").getAsString()));
        }
      }
    };

    addListener(handler);

    workerPool.submit(() -> {
      try {
        if ("curseForge".equalsIgnoreCase(platform) || "curseforge".equalsIgnoreCase(platform)) {
          CurseForge cf = new CurseForge("search", query, version, loader, curseAPI, "");
          cf.handler();
        } else {
          Modrinth m = new Modrinth("search", query, version, loader, email);
          m.handler();
        }
      } catch (Exception e) {
        removeListener(handler);
        future.completeExceptionally(e);
      }
    });

    Executors.newSingleThreadScheduledExecutor().schedule(() -> {
      if (!future.isDone()) {
        removeListener(handler);
        future.completeExceptionally(new TimeoutException("Mod search timed out"));
      }
    }, 15, TimeUnit.SECONDS);

    return future;
  }

  public CompletableFuture<List<ModVersionInfo>> getModVersions(String platform, String modIdOrSlug, String gameVersion,
      String loader) {
    CompletableFuture<List<ModVersionInfo>> future = new CompletableFuture<>();
    workerPool.submit(() -> {
      try {
        List<ModVersionInfo> list = new ArrayList<>();
        if (!"curseForge".equalsIgnoreCase(platform) && !"curseforge".equalsIgnoreCase(platform)) {
          String encLoader = URLEncoder.encode("[\"" + loader + "\"]", StandardCharsets.UTF_8);
          String encVersion = URLEncoder.encode("[\"" + gameVersion + "\"]", StandardCharsets.UTF_8);
          String url = "https://api.modrinth.com/v2/project/" + modIdOrSlug + "/version?loaders=" + encLoader
              + "&game_versions=" + encVersion;
          HttpRequest req = HttpRequest.newBuilder()
              .uri(URI.create(url))
              .header("User-Agent", "DeployedReject/MurCes/1.6.0 (" + email + ")")
              .GET()
              .build();
          HttpResponse<String> resp = NetworkUtils.attemptS(req);
          if (resp != null && resp.statusCode() == 200) {
            JsonArray arr = com.google.gson.JsonParser.parseString(resp.body()).getAsJsonArray();
            for (JsonElement el : arr) {
              if (el.isJsonObject()) {
                JsonObject obj = el.getAsJsonObject();
                String vId = obj.get("id").getAsString();
                String vName = obj.has("name") ? obj.get("name").getAsString() : "";
                String vNum = obj.has("version_number") ? obj.get("version_number").getAsString() : "";
                String dUrl = "";
                String fn = "";
                long sz = -1;
                if (obj.has("files") && obj.getAsJsonArray("files").size() > 0) {
                  JsonObject f = obj.getAsJsonArray("files").get(0).getAsJsonObject();
                  dUrl = f.get("url").getAsString();
                  fn = f.get("filename").getAsString();
                  sz = f.has("size") ? f.get("size").getAsLong() : -1;
                }
                list.add(new ModVersionInfo(vId, vName, vNum, dUrl, fn, sz));
              }
            }
          }
        } else {
          int loaderType = 4;
          if ("forge".equalsIgnoreCase(loader))
            loaderType = 1;
          else if ("cauldron".equalsIgnoreCase(loader))
            loaderType = 2;
          else if ("liteLoader".equalsIgnoreCase(loader))
            loaderType = 3;
          else if ("quilt".equalsIgnoreCase(loader))
            loaderType = 5;
          else if ("neoForge".equalsIgnoreCase(loader))
            loaderType = 6;

          String url = "https://api.curseforge.com/v1/mods/" + modIdOrSlug + "/files?gameVersion=" + gameVersion
              + "&modLoaderType=" + loaderType;
          HttpRequest req = HttpRequest.newBuilder()
              .uri(URI.create(url))
              .header("x-api-key", curseAPI)
              .header("Accept", "application/json")
              .GET()
              .build();
          HttpResponse<String> resp = NetworkUtils.attemptS(req);
          if (resp != null && resp.statusCode() == 200) {
            JsonObject root = com.google.gson.JsonParser.parseString(resp.body()).getAsJsonObject();
            if (root.has("data") && root.get("data").isJsonArray()) {
              JsonArray data = root.getAsJsonArray("data");
              for (JsonElement el : data) {
                if (el.isJsonObject()) {
                  JsonObject obj = el.getAsJsonObject();
                  String vId = obj.get("id").getAsString();
                  String vName = obj.has("displayName") ? obj.get("displayName").getAsString() : "";
                  String fn = obj.has("fileName") ? obj.get("fileName").getAsString() : "";
                  String dUrl = (obj.has("downloadUrl") && !obj.get("downloadUrl").isJsonNull())
                      ? obj.get("downloadUrl").getAsString()
                      : "";
                  long sz = obj.has("fileLength") ? obj.get("fileLength").getAsLong() : -1;
                  list.add(new ModVersionInfo(vId, vName, fn, dUrl, fn, sz));
                }
              }
            }
          }
        }
        future.complete(list);
      } catch (Exception e) {
        future.completeExceptionally(e);
      }
    });
    return future;
  }

  public CompletableFuture<String> getModFullDescription(String platform, String modIdOrSlug) {
    CompletableFuture<String> future = new CompletableFuture<>();
    if (modIdOrSlug == null || modIdOrSlug.trim().isEmpty()) {
      future.complete("");
      return future;
    }
    workerPool.submit(() -> {
      try {
        String cleanId = URLEncoder.encode(modIdOrSlug.trim(), StandardCharsets.UTF_8);
        if ("curseForge".equalsIgnoreCase(platform) || "curseforge".equalsIgnoreCase(platform)) {
          String url = "https://api.curseforge.com/v1/mods/" + cleanId + "/description";
          HttpRequest req = HttpRequest.newBuilder()
              .uri(URI.create(url))
              .header("x-api-key", curseAPI != null ? curseAPI : "")
              .header("Accept", "application/json")
              .GET()
              .build();
          HttpResponse<String> resp = NetworkUtils.attemptS(req);
          if (resp != null && resp.statusCode() == 200) {
            JsonObject root = com.google.gson.JsonParser.parseString(resp.body()).getAsJsonObject();
            if (root.has("data") && !root.get("data").isJsonNull()) {
              String rawHtml = root.get("data").getAsString();
              future.complete(cleanHtml(rawHtml));
              return;
            }
          }
        } else {
          String url = "https://api.modrinth.com/v2/project/" + cleanId;
          HttpRequest req = HttpRequest.newBuilder()
              .uri(URI.create(url))
              .header("User-Agent", "DeployedReject/MurCes/1.6.0 (" + email + ")")
              .GET()
              .build();
          HttpResponse<String> resp = NetworkUtils.attemptS(req);
          if (resp != null && resp.statusCode() == 200) {
            JsonObject root = com.google.gson.JsonParser.parseString(resp.body()).getAsJsonObject();
            if (root.has("body") && !root.get("body").isJsonNull()) {
              String body = root.get("body").getAsString();
              future.complete(cleanMarkdown(body));
              return;
            }
          }
        }
        future.complete("");
      } catch (Exception e) {
        future.completeExceptionally(e);
      }
    });
    return future;
  }

  public static String cleanHtml(String html) {
    if (html == null)
      return "";
    String s = html;
    s = s.replaceAll("(?i)<br\\s*/?>", "\n");
    s = s.replaceAll("(?i)</p>", "\n\n");
    s = s.replaceAll("(?i)</li>", "\n");
    s = s.replaceAll("(?i)<li[^>]*>", "  • ");
    s = s.replaceAll("(?i)</h[1-6]>", "\n\n");
    s = s.replaceAll("<[^>]+>", "");
    s = s.replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&mdash;", "—")
        .replace("&ndash;", "–");
    s = s.replaceAll("\n{3,}", "\n\n");
    return s.trim();
  }

  public static String cleanMarkdown(String md) {
    if (md == null)
      return "";
    String s = md;

    s = s.replaceAll("!\\[[^\\]]*\\]\\([^)]*\\)", "");

    s = s.replaceAll("\\[\\s*\\]\\([^)]*\\)", "");

    s = s.replaceAll("\\[([^\\]]+)\\]\\([^)]*\\)", "$1");

    s = s.replaceAll("<[^>]+>", "");

    s = s.replaceAll("(?m)^#{1,6}\\s*", "◆ ");

    s = s.replaceAll("\\*\\*([^*]+)\\*\\*", "$1");
    s = s.replaceAll("__([^_]+)__", "$1");
    s = s.replaceAll("(?m)^[-*]\\s+", "  • ");

    s = s.replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&mdash;", "—")
        .replace("&ndash;", "–");
    s = s.replaceAll("\n{3,}", "\n\n");
    return s.trim();
  }

  public CompletableFuture<Boolean> downloadMod(String platform, String modIdOrSlug, String version, String loader,
      Consumer<Double> progressCallback) {
    return downloadMod(platform, modIdOrSlug, version, loader, progressCallback, null);
  }

  public CompletableFuture<Boolean> downloadMod(String platform, String modIdOrSlug, String version, String loader,
      Consumer<Double> progressCallback,
      Consumer<DownloadProgressInfo> richProgressCallback) {
    var compat = checkCompatibility(loader, version);
    if (!compat.isCompatible()) {
      log("[ERR:] " + compat.getMessage());
      CompletableFuture<Boolean> errFuture = new CompletableFuture<>();
      errFuture.completeExceptionally(new IllegalArgumentException(compat.getMessage()));
      return errFuture;
    }

    CompletableFuture<Boolean> future = new CompletableFuture<>();
    long startTime = System.currentTimeMillis();
    String jobId = "mod-" + modIdOrSlug;

    final JobTracker.TrackedJob trackedJob = JobTracker.getInstance().registerJob(
        jobId,
        "Download Mod (" + platform + "): " + modIdOrSlug,
        "Mod",
        () -> future.cancel(true));

    Consumer<JsonObject> handler = new Consumer<>() {
      @Override
      public void accept(JsonObject json) {
        if (trackedJob.isCancelled()) {
          removeListener(this);
          JobTracker.getInstance().unregisterJob(jobId);
          future.completeExceptionally(new CancellationException("Mod download cancelled"));
          return;
        }
        if (json.has("type") && "download".equals(json.get("type").getAsString())) {
          int status = json.has("status") ? json.get("status").getAsInt() : -1;
          if (json.has("progress")) {
            double pct = json.get("progress").getAsDouble();
            if (pct >= 0) {
              trackedJob.setProgress(pct);
              trackedJob.setStatus(String.format("%.1f%%", pct));
            }
            if (progressCallback != null) {
              progressCallback.accept(pct);
            }
            if (richProgressCallback != null) {
              long read = json.has("read") ? json.get("read").getAsLong() : 0L;
              long total = json.has("total") ? json.get("total").getAsLong() : 0L;
              long now = System.currentTimeMillis();
              double elapsedSec = Math.max(0.001, (now - startTime) / 1000.0);
              double speedMBps = (read / (1024.0 * 1024.0)) / elapsedSec;
              int eta = (total > read && speedMBps > 0)
                  ? (int) Math.round(((total - read) / (1024.0 * 1024.0)) / speedMBps)
                  : 0;
              richProgressCallback.accept(new DownloadProgressInfo(pct, read, total, speedMBps, eta));
            }
          }
          if (status == 3) {
            removeListener(this);
            JobTracker.getInstance().unregisterJob(jobId);
            future.complete(true);
          }
        } else if (json.has("error")) {
          removeListener(this);
          JobTracker.getInstance().unregisterJob(jobId);
          future.completeExceptionally(new RuntimeException(json.get("error").getAsString()));
        }
      }
    };

    addListener(handler);

    workerPool.submit(() -> {
      try {
        if ("curseForge".equalsIgnoreCase(platform) || "curseforge".equalsIgnoreCase(platform)) {
          CurseForge cf = new CurseForge("download", modIdOrSlug, version, loader, curseAPI, modIdOrSlug);
          cf.handler();
        } else {
          Modrinth m = new Modrinth("download", modIdOrSlug, version, loader, email);
          m.handler();
        }
      } catch (Exception e) {
        removeListener(handler);
        JobTracker.getInstance().unregisterJob(jobId);
        future.completeExceptionally(e);
      }
    });

    return future;
  }

  public CompletableFuture<Boolean> downloadModDirect(String downloadUrl, String filename,
      Consumer<DownloadProgressInfo> richProgressCallback) {
    return downloadModDirect(downloadUrl, filename, null, null, richProgressCallback);
  }

  public CompletableFuture<Boolean> downloadModDirect(String downloadUrl, String filename,
      String loader, String gameVersion,
      Consumer<DownloadProgressInfo> richProgressCallback) {
    var compat = checkCompatibility(loader, gameVersion);
    if (!compat.isCompatible()) {
      log("[ERR:] " + compat.getMessage());
      CompletableFuture<Boolean> errFuture = new CompletableFuture<>();
      errFuture.completeExceptionally(new IllegalArgumentException(compat.getMessage()));
      return errFuture;
    }

    CompletableFuture<Boolean> future = new CompletableFuture<>();
    String jobId = "mod-direct-" + filename;
    File targetTmp = new File("mods", filename + ".tmp");
    File targetFinal = new File("mods", filename);

    final Thread[] workerThread = new Thread[1];
    final JobTracker.TrackedJob trackedJob = JobTracker.getInstance().registerJob(
        jobId,
        "Download Mod: " + filename,
        "Mod",
        () -> {
          if (workerThread[0] != null) {
            workerThread[0].interrupt();
          }
          if (targetTmp.exists()) {
            try {
              targetTmp.delete();
            } catch (Exception ignored) {
            }
          }
        });
    trackedJob.addFileToCleanup(targetTmp);

    workerPool.submit(() -> {
      workerThread[0] = Thread.currentThread();
      boolean finishedSuccessfully = false;
      try {
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(downloadUrl))
            .header("User-Agent", "DeployedReject/MurCes/1.6.0 (" + email + ")")
            .GET()
            .build();
        HttpResponse<InputStream> resp = NetworkUtils.attemptI(req);
        if (resp == null || resp.statusCode() != 200) {
          JobTracker.getInstance().unregisterJob(jobId);
          future.completeExceptionally(
              new IOException("Failed to download mod file: HTTP " + (resp != null ? resp.statusCode() : "null")));
          return;
        }
        long filesize = resp.headers().firstValueAsLong("content-length").orElse(-1L);
        long startTime = System.currentTimeMillis();

        File modsDir = new File("mods");
        if (!modsDir.exists())
          modsDir.mkdirs();

        try (InputStream in = resp.body(); FileOutputStream out = new FileOutputStream(targetTmp)) {
          byte[] buf = new byte[8192];
          int n;
          long totalRead = 0;
          long lastCallback = 0;
          while ((n = in.read(buf)) != -1) {
            if (Thread.currentThread().isInterrupted() || trackedJob.isCancelled()) {
              throw new InterruptedException("Download cancelled");
            }
            out.write(buf, 0, n);
            totalRead += n;
            long now = System.currentTimeMillis();
            if (now - lastCallback >= 500 || (filesize > 0 && totalRead == filesize)) {
              lastCallback = now;
              double elapsedSec = Math.max(0.001, (now - startTime) / 1000.0);
              double speedMBps = (totalRead / (1024.0 * 1024.0)) / elapsedSec;
              int eta = (filesize > totalRead && speedMBps > 0)
                  ? (int) Math.round(((filesize - totalRead) / (1024.0 * 1024.0)) / speedMBps)
                  : 0;
              double pct = filesize > 0 ? (totalRead * 100.0) / filesize : -1.0;
              if (pct >= 0) {
                trackedJob.setProgress(pct);
                trackedJob.setStatus(String.format("%.1f%% (ETA: %ds @ %.1f MB/s)", pct, eta, speedMBps));
              }
              if (richProgressCallback != null) {
                richProgressCallback.accept(new DownloadProgressInfo(pct, totalRead, filesize, speedMBps, eta));
              }
            }
          }
          out.flush();
        }

        try {
          Files.move(targetTmp.toPath(), targetFinal.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING,
              java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception moveEx) {
          Files.move(targetTmp.toPath(), targetFinal.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }

        try {
          ModUpdateManager.ModFileInfo parsed = ModUpdateManager.parseModFile(targetFinal);
          if (parsed != null && !parsed.slug.isEmpty()) {
            ModUpdateManager.cleanOldModVersions(parsed.slug, targetFinal.getName());
          }
        } catch (Exception ignored) {
        }
        finishedSuccessfully = true;
        JobTracker.getInstance().unregisterJob(jobId);
        future.complete(true);
      } catch (Exception e) {
        if (!finishedSuccessfully && targetTmp.exists()) {
          try {
            targetTmp.delete();
          } catch (Exception ignored) {
          }
        }
        JobTracker.getInstance().unregisterJob(jobId);
        future.completeExceptionally(e);
      }
    });
    return future;
  }

  public CompletableFuture<List<ModpackResult>> searchModpacks(String platform, String query, String version, String loader) {
    CompletableFuture<List<ModpackResult>> future = new CompletableFuture<>();
    workerPool.submit(() -> {
      try {
        List<ModpackResult> list = new ArrayList<>();
        if ("curseForge".equalsIgnoreCase(platform) || "curseforge".equalsIgnoreCase(platform)) {
          int loaderType = 4;
          if ("forge".equalsIgnoreCase(loader)) loaderType = 1;
          else if ("cauldron".equalsIgnoreCase(loader)) loaderType = 2;
          else if ("liteLoader".equalsIgnoreCase(loader)) loaderType = 3;
          else if ("quilt".equalsIgnoreCase(loader)) loaderType = 5;
          else if ("neoForge".equalsIgnoreCase(loader)) loaderType = 6;

          String q = URLEncoder.encode(query != null ? query.trim() : "", StandardCharsets.UTF_8);
          String gVer = version != null && !"any".equalsIgnoreCase(version) ? URLEncoder.encode(version.trim(), StandardCharsets.UTF_8) : "";
          String url = "https://api.curseforge.com/v1/mods/search?gameId=432&classId=4471&searchFilter=" + q;
          if (!gVer.isEmpty()) url += "&gameVersion=" + gVer;
          url += "&modLoaderType=" + loaderType + "&pageSize=15";
          HttpRequest req = HttpRequest.newBuilder()
              .uri(URI.create(url))
              .header("x-api-key", curseAPI != null ? curseAPI : "")
              .header("Accept", "application/json")
              .GET()
              .build();
          HttpResponse<String> resp = NetworkUtils.attemptS(req);
          if (resp != null && resp.statusCode() == 200) {
            JsonObject root = com.google.gson.JsonParser.parseString(resp.body()).getAsJsonObject();
            if (root.has("data") && root.get("data").isJsonArray()) {
              JsonArray arr = root.getAsJsonArray("data");
              for (JsonElement el : arr) {
                if (el.isJsonObject()) {
                  JsonObject o = el.getAsJsonObject();
                  String id = o.has("id") ? o.get("id").getAsString() : "";
                  String slug = o.has("slug") ? o.get("slug").getAsString() : id;
                  String name = o.has("name") ? o.get("name").getAsString() : "";
                  String author = "";
                  if (o.has("authors") && o.get("authors").isJsonArray() && o.getAsJsonArray("authors").size() > 0) {
                    JsonObject firstAuthor = o.getAsJsonArray("authors").get(0).getAsJsonObject();
                    if (firstAuthor.has("name") && !firstAuthor.get("name").isJsonNull()) {
                      author = firstAuthor.get("name").getAsString();
                    }
                  }
                  String summary = o.has("summary") && !o.get("summary").isJsonNull() ? o.get("summary").getAsString() : "";
                  int dls = o.has("downloadCount") ? o.get("downloadCount").getAsInt() : 0;
                  list.add(new ModpackResult(id, slug, name, author, summary, dls));
                }
              }
            }
          }
        } else {
          // Modrinth
          String encQuery = URLEncoder.encode(query != null ? query.trim() : "", StandardCharsets.UTF_8);
          String facets;
          if (version != null && !version.isEmpty() && loader != null && !loader.isEmpty()) {
            facets = "[[\"project_type:modpack\"],[\"versions:" + version + "\"],[\"categories:" + loader + "\"]]";
          } else {
            facets = "[[\"project_type:modpack\"]]";
          }
          String encFacets = URLEncoder.encode(facets, StandardCharsets.UTF_8);
          String url = "https://api.modrinth.com/v2/search?query=" + encQuery + "&facets=" + encFacets + "&limit=15";
          HttpRequest req = HttpRequest.newBuilder()
              .uri(URI.create(url))
              .header("User-Agent", "DeployedReject/MurCes/1.6.0 (" + email + ")")
              .GET()
              .build();
          HttpResponse<String> resp = NetworkUtils.attemptS(req);
          if (resp != null && resp.statusCode() == 200) {
            JsonObject root = com.google.gson.JsonParser.parseString(resp.body()).getAsJsonObject();
            if (root.has("hits") && root.get("hits").isJsonArray()) {
              JsonArray arr = root.getAsJsonArray("hits");
              for (JsonElement el : arr) {
                if (el.isJsonObject()) {
                  JsonObject o = el.getAsJsonObject();
                  String id = o.has("project_id") ? o.get("project_id").getAsString() : "";
                  String slug = o.has("slug") && !o.get("slug").isJsonNull() ? o.get("slug").getAsString() : id;
                  String name = o.has("title") && !o.get("title").isJsonNull() ? o.get("title").getAsString() : slug;
                  String author = o.has("author") && !o.get("author").isJsonNull() ? o.get("author").getAsString() : "";
                  String desc = o.has("description") && !o.get("description").isJsonNull() ? o.get("description").getAsString() : "";
                  int dls = o.has("downloads") ? o.get("downloads").getAsInt() : 0;
                  list.add(new ModpackResult(id, slug, name, author, desc, dls));
                }
              }
            }
          }
        }
        future.complete(list);
      } catch (Exception e) {
        future.completeExceptionally(e);
      }
    });
    return future;
  }

  public CompletableFuture<List<ModpackVersionInfo>> getModpackVersions(String platform, String modpackIdOrSlug,
      String gameVersion, String loader) {
    CompletableFuture<List<ModpackVersionInfo>> future = new CompletableFuture<>();
    workerPool.submit(() -> {
      try {
        List<ModpackVersionInfo> list = new ArrayList<>();
        if ("curseForge".equalsIgnoreCase(platform) || "curseforge".equalsIgnoreCase(platform)) {
          int loaderType = 4;
          if ("forge".equalsIgnoreCase(loader)) loaderType = 1;
          else if ("cauldron".equalsIgnoreCase(loader)) loaderType = 2;
          else if ("liteLoader".equalsIgnoreCase(loader)) loaderType = 3;
          else if ("quilt".equalsIgnoreCase(loader)) loaderType = 5;
          else if ("neoForge".equalsIgnoreCase(loader)) loaderType = 6;

          String url = "https://api.curseforge.com/v1/mods/" + modpackIdOrSlug + "/files?gameVersion=" + gameVersion
              + "&modLoaderType=" + loaderType;
          HttpRequest req = HttpRequest.newBuilder()
              .uri(URI.create(url))
              .header("x-api-key", curseAPI != null ? curseAPI : "")
              .header("Accept", "application/json")
              .GET()
              .build();
          HttpResponse<String> resp = NetworkUtils.attemptS(req);
          if (resp != null && resp.statusCode() == 200) {
            JsonObject root = com.google.gson.JsonParser.parseString(resp.body()).getAsJsonObject();
            if (root.has("data") && root.get("data").isJsonArray()) {
              JsonArray data = root.getAsJsonArray("data");
              for (JsonElement el : data) {
                if (el.isJsonObject()) {
                  JsonObject obj = el.getAsJsonObject();
                  String vId = obj.get("id").getAsString();
                  String vName = obj.has("displayName") ? obj.get("displayName").getAsString() : "";
                  String fn = obj.has("fileName") ? obj.get("fileName").getAsString() : "";
                  String dUrl = (obj.has("downloadUrl") && !obj.get("downloadUrl").isJsonNull())
                      ? obj.get("downloadUrl").getAsString() : "";
                  long sz = obj.has("fileLength") ? obj.get("fileLength").getAsLong() : -1;
                  int depCount = (obj.has("dependencies") && obj.get("dependencies").isJsonArray())
                      ? obj.getAsJsonArray("dependencies").size() : 0;
                  list.add(new ModpackVersionInfo(vId, vName, fn, dUrl, fn, sz, depCount));
                }
              }
            }
          }
        } else {
          // Modrinth
          String encLoader = URLEncoder.encode("[\"" + loader + "\"]", StandardCharsets.UTF_8);
          String encVersion = URLEncoder.encode("[\"" + gameVersion + "\"]", StandardCharsets.UTF_8);
          String url = "https://api.modrinth.com/v2/project/" + modpackIdOrSlug + "/version?loaders=" + encLoader
              + "&game_versions=" + encVersion;
          HttpRequest req = HttpRequest.newBuilder()
              .uri(URI.create(url))
              .header("User-Agent", "DeployedReject/MurCes/1.6.0 (" + email + ")")
              .GET()
              .build();
          HttpResponse<String> resp = NetworkUtils.attemptS(req);
          if (resp == null || resp.statusCode() != 200 || resp.body().trim().equals("[]")) {
            // Fallback: fetch all versions
            url = "https://api.modrinth.com/v2/project/" + modpackIdOrSlug + "/version";
            req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "DeployedReject/MurCes/1.6.0 (" + email + ")")
                .GET()
                .build();
            resp = NetworkUtils.attemptS(req);
          }

          if (resp != null && resp.statusCode() == 200) {
            JsonArray arr = com.google.gson.JsonParser.parseString(resp.body()).getAsJsonArray();
            for (JsonElement el : arr) {
              if (el.isJsonObject()) {
                JsonObject obj = el.getAsJsonObject();
                String vId = obj.get("id").getAsString();
                String vName = obj.has("name") ? obj.get("name").getAsString() : "";
                String vNum = obj.has("version_number") ? obj.get("version_number").getAsString() : "";
                String mrpackUrl = "";
                String fn = "";
                long sz = -1;
                if (obj.has("files") && obj.getAsJsonArray("files").size() > 0) {
                  for (JsonElement fe : obj.getAsJsonArray("files")) {
                    if (fe.isJsonObject()) {
                      JsonObject fObj = fe.getAsJsonObject();
                      String fname = fObj.has("filename") ? fObj.get("filename").getAsString() : "";
                      if (fname.endsWith(".mrpack") || mrpackUrl.isEmpty()) {
                        mrpackUrl = fObj.has("url") ? fObj.get("url").getAsString() : "";
                        fn = fname;
                        sz = fObj.has("size") ? fObj.get("size").getAsLong() : -1;
                        if (fname.endsWith(".mrpack")) break;
                      }
                    }
                  }
                }
                int depCount = (obj.has("dependencies") && obj.get("dependencies").isJsonArray())
                    ? obj.getAsJsonArray("dependencies").size() : 0;
                list.add(new ModpackVersionInfo(vId, vName, vNum, mrpackUrl, fn, sz, depCount));
              }
            }
          }
        }
        future.complete(list);
      } catch (Exception e) {
        future.completeExceptionally(e);
      }
    });
    return future;
  }

  public CompletableFuture<List<ModpackDependencyInfo>> getModpackDependencies(String platform, String versionId) {
    CompletableFuture<List<ModpackDependencyInfo>> future = new CompletableFuture<>();
    if (versionId == null || versionId.trim().isEmpty()) {
      future.complete(Collections.emptyList());
      return future;
    }
    workerPool.submit(() -> {
      try {
        List<ModpackDependencyInfo> deps = new ArrayList<>();
        if ("curseForge".equalsIgnoreCase(platform) || "curseforge".equalsIgnoreCase(platform)) {
          future.complete(deps);
          return;
        }

        // Modrinth version dependencies
        String url = "https://api.modrinth.com/v2/version/" + versionId.trim();
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("User-Agent", "DeployedReject/MurCes/1.6.0 (" + email + ")")
            .GET()
            .build();
        HttpResponse<String> resp = NetworkUtils.attemptS(req);
        if (resp != null && resp.statusCode() == 200) {
          JsonObject vObj = com.google.gson.JsonParser.parseString(resp.body()).getAsJsonObject();
          if (vObj.has("dependencies") && vObj.get("dependencies").isJsonArray()) {
            JsonArray depArr = vObj.getAsJsonArray("dependencies");
            List<String> versionIds = new ArrayList<>();
            Map<String, String> depTypes = new HashMap<>();
            for (JsonElement de : depArr) {
              if (de.isJsonObject()) {
                JsonObject dObj = de.getAsJsonObject();
                if (dObj.has("version_id") && !dObj.get("version_id").isJsonNull()) {
                  String vid = dObj.get("version_id").getAsString();
                  versionIds.add(vid);
                  String dt = dObj.has("dependency_type") ? dObj.get("dependency_type").getAsString() : "required";
                  depTypes.put(vid, dt);
                }
              }
            }

            int chunkSize = 80;
            for (int i = 0; i < versionIds.size(); i += chunkSize) {
              List<String> chunk = versionIds.subList(i, Math.min(i + chunkSize, versionIds.size()));
              JsonArray idsArr = new JsonArray();
              for (String id : chunk) {
                idsArr.add(id);
              }
              String idsParam = URLEncoder.encode(idsArr.toString(), StandardCharsets.UTF_8);
              String batchUrl = "https://api.modrinth.com/v2/versions?ids=" + idsParam;
              HttpRequest batchReq = HttpRequest.newBuilder()
                  .uri(URI.create(batchUrl))
                  .header("User-Agent", "DeployedReject/MurCes/1.6.0 (" + email + ")")
                  .GET()
                  .build();
              HttpResponse<String> batchResp = NetworkUtils.attemptS(batchReq);
              if (batchResp != null && batchResp.statusCode() == 200) {
                JsonArray verArray = com.google.gson.JsonParser.parseString(batchResp.body()).getAsJsonArray();
                for (JsonElement ve : verArray) {
                  if (ve.isJsonObject()) {
                    JsonObject vo = ve.getAsJsonObject();
                    String vid = vo.get("id").getAsString();
                    String pid = vo.has("project_id") ? vo.get("project_id").getAsString() : "";
                    String vname = vo.has("name") ? vo.get("name").getAsString() : "";
                    String env = vo.has("environment") && !vo.get("environment").isJsonNull() ? vo.get("environment").getAsString() : "client_and_server";
                    String dUrl = "";
                    String fn = "";
                    long sz = -1;
                    if (vo.has("files") && vo.getAsJsonArray("files").size() > 0) {
                      for (JsonElement fe : vo.getAsJsonArray("files")) {
                        if (fe.isJsonObject()) {
                          JsonObject fo = fe.getAsJsonObject();
                          boolean isPrimary = fo.has("primary") && fo.get("primary").getAsBoolean();
                          String fname = fo.has("filename") ? fo.get("filename").getAsString() : "";
                          if (isPrimary || fname.endsWith(".jar") || dUrl.isEmpty()) {
                            dUrl = fo.has("url") ? fo.get("url").getAsString() : "";
                            fn = fname;
                            sz = fo.has("size") ? fo.get("size").getAsLong() : -1;
                            if (isPrimary || fname.endsWith(".jar")) break;
                          }
                        }
                      }
                    }
                    String dtype = depTypes.getOrDefault(vid, "required");
                    deps.add(new ModpackDependencyInfo(pid, vid, vname, fn, dUrl, sz, env, dtype));
                  }
                }
              }
            }
          }
        }
        future.complete(deps);
      } catch (Exception e) {
        future.completeExceptionally(e);
      }
    });
    return future;
  }

  public CompletableFuture<ModpackInstallSummary> installModpack(
      String platform,
      String modpackName,
      String versionId,
      String mrpackUrl,
      String gameVersion,
      String loader,
      boolean includeClientMods,
      Consumer<String> statusCallback,
      Consumer<Double> overallProgressCallback,
      Consumer<DownloadProgressInfo> fileProgressCallback) {

    var compat = checkCompatibility(loader, gameVersion);
    if (!compat.isCompatible()) {
      log("[ERR:] " + compat.getMessage());
      CompletableFuture<ModpackInstallSummary> errFuture = new CompletableFuture<>();
      errFuture.completeExceptionally(new IllegalArgumentException(compat.getMessage()));
      return errFuture;
    }

    CompletableFuture<ModpackInstallSummary> future = new CompletableFuture<>();
    String jobId = "modpack-" + (versionId != null ? versionId : modpackName);

    final Thread[] currentWorker = new Thread[1];
    final JobTracker.TrackedJob trackedJob = JobTracker.getInstance().registerJob(
        jobId,
        "Install Modpack: " + modpackName,
        "Modpack",
        () -> {
          if (currentWorker[0] != null) {
            currentWorker[0].interrupt();
          }
        });

    workerPool.submit(() -> {
      currentWorker[0] = Thread.currentThread();
      try {
        if (statusCallback != null) {
          statusCallback.accept("Resolving modpack dependencies from version metadata...");
        }
        trackedJob.setStatus("Resolving metadata...");

        List<ModpackDependencyInfo> allDeps = getModpackDependencies(platform, versionId).get(30, TimeUnit.SECONDS);

        // Extract overrides from .mrpack if mrpackUrl is present
        if (mrpackUrl != null && !mrpackUrl.isEmpty() && mrpackUrl.endsWith(".mrpack")) {
          try {
            if (statusCallback != null) {
              statusCallback.accept("Downloading modpack archive for configurations & overrides...");
            }
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(mrpackUrl))
                .header("User-Agent", "DeployedReject/MurCes/1.6.0 (" + email + ")")
                .GET()
                .build();
            HttpResponse<InputStream> resp = NetworkUtils.attemptI(req);
            if (resp != null && resp.statusCode() == 200) {
              try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(resp.body())) {
                java.util.zip.ZipEntry ze;
                File targetBase = new File(".");
                while ((ze = zis.getNextEntry()) != null) {
                  String name = ze.getName();
                  if (name.startsWith("overrides/") && !ze.isDirectory()) {
                    String relative = name.substring("overrides/".length());
                    File targetFile = new File(targetBase, relative);
                    if (targetFile.getCanonicalPath().startsWith(targetBase.getCanonicalPath())) {
                      File parent = targetFile.getParentFile();
                      if (parent != null && !parent.exists()) {
                        parent.mkdirs();
                      }
                      try (FileOutputStream fos = new FileOutputStream(targetFile)) {
                        byte[] buffer = new byte[8192];
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                          fos.write(buffer, 0, len);
                        }
                      }
                    }
                  }
                  zis.closeEntry();
                }
              }
            }
          } catch (Exception e) {
            // Non-fatal override extraction failure
          }
        }

        File modsDir = new File("mods");
        if (!modsDir.exists()) {
          modsDir.mkdirs();
        }

        int totalDeps = allDeps.size();
        List<ModpackDependencyInfo> toInstall = new ArrayList<>();
        int skippedClient = 0;

        for (ModpackDependencyInfo dep : allDeps) {
          if (!includeClientMods && dep.isClientOnly()) {
            skippedClient++;
          } else {
            toInstall.add(dep);
          }
        }

        int installedCount = 0;
        int errorCount = 0;
        long totalBytesDownloaded = 0;
        List<String> installedModNames = new ArrayList<>();

        int totalToInstall = toInstall.size();
        for (int i = 0; i < totalToInstall; i++) {
          if (Thread.currentThread().isInterrupted() || trackedJob.isCancelled()) {
            throw new InterruptedException("Modpack installation cancelled by user.");
          }

          ModpackDependencyInfo dep = toInstall.get(i);
          String filename = dep.filename;
          if (filename == null || filename.isEmpty()) {
            filename = dep.name.toLowerCase().replaceAll("[^a-z0-9_.-]", "") + ".jar";
          }
          if (!filename.endsWith(".jar")) {
            filename += ".jar";
          }

          String stepMsg = String.format("[%d/%d] %s (%s)", (i + 1), totalToInstall, dep.name.isEmpty() ? filename : dep.name, dep.formattedEnvironment());
          if (statusCallback != null) {
            statusCallback.accept(stepMsg);
          }
          trackedJob.setStatus(stepMsg);

          double overallPct = (i * 100.0) / Math.max(1, totalToInstall);
          if (overallProgressCallback != null) {
            overallProgressCallback.accept(overallPct);
          }
          trackedJob.setProgress(overallPct);

          if (dep.downloadUrl == null || dep.downloadUrl.isEmpty()) {
            errorCount++;
            continue;
          }

          File targetTmp = new File(modsDir, filename + ".tmp");
          File targetFinal = new File(modsDir, filename);

          try {
            HttpRequest dlReq = HttpRequest.newBuilder()
                .uri(URI.create(dep.downloadUrl))
                .header("User-Agent", "DeployedReject/MurCes/1.6.0 (" + email + ")")
                .GET()
                .build();
            HttpResponse<InputStream> dlResp = NetworkUtils.attemptI(dlReq);
            if (dlResp == null || dlResp.statusCode() != 200) {
              errorCount++;
              continue;
            }

            long filesize = dlResp.headers().firstValueAsLong("content-length").orElse(dep.sizeBytes);
            long fileStart = System.currentTimeMillis();

            try (InputStream in = dlResp.body(); FileOutputStream out = new FileOutputStream(targetTmp)) {
              byte[] buf = new byte[8192];
              int n;
              long read = 0;
              long lastCb = 0;
              while ((n = in.read(buf)) != -1) {
                if (Thread.currentThread().isInterrupted() || trackedJob.isCancelled()) {
                  targetTmp.delete();
                  throw new InterruptedException("Download cancelled");
                }
                out.write(buf, 0, n);
                read += n;
                long now = System.currentTimeMillis();
                if (now - lastCb >= 300 || (filesize > 0 && read == filesize)) {
                  lastCb = now;
                  double elapsed = Math.max(0.001, (now - fileStart) / 1000.0);
                  double speed = (read / (1024.0 * 1024.0)) / elapsed;
                  int eta = (filesize > read && speed > 0)
                      ? (int) Math.round(((filesize - read) / (1024.0 * 1024.0)) / speed)
                      : 0;
                  double filePct = filesize > 0 ? (read * 100.0) / filesize : -1.0;
                  if (fileProgressCallback != null) {
                    fileProgressCallback.accept(new DownloadProgressInfo(filePct, read, filesize, speed, eta));
                  }
                }
              }
              out.flush();
            }

            try {
              Files.move(targetTmp.toPath(), targetFinal.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                  java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (Exception moveEx) {
              Files.move(targetTmp.toPath(), targetFinal.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }

            installedCount++;
            totalBytesDownloaded += targetFinal.length();
            installedModNames.add(filename);

            try {
              ModUpdateManager.ModFileInfo parsed = ModUpdateManager.parseModFile(targetFinal);
              if (parsed != null && !parsed.slug.isEmpty()) {
                ModUpdateManager.cleanOldModVersions(parsed.slug, targetFinal.getName());
              }
            } catch (Exception ignored) {}

          } catch (InterruptedException ie) {
            targetTmp.delete();
            throw ie;
          } catch (Exception ex) {
            targetTmp.delete();
            errorCount++;
          }
        }

        if (overallProgressCallback != null) {
          overallProgressCallback.accept(100.0);
        }
        trackedJob.setProgress(100.0);
        trackedJob.setStatus("Completed (" + installedCount + " installed)");

        ModpackInstallSummary summary = new ModpackInstallSummary(
            modpackName, totalDeps, installedCount, skippedClient, errorCount, totalBytesDownloaded, installedModNames);

        JobTracker.getInstance().unregisterJob(jobId);
        future.complete(summary);

      } catch (Exception e) {
        JobTracker.getInstance().unregisterJob(jobId);
        future.completeExceptionally(e);
      }
    });

    return future;
  }

  private static final java.util.concurrent.atomic.AtomicBoolean serverInstalling = new java.util.concurrent.atomic.AtomicBoolean(
      false);

  public static boolean isServerDownloading() {
    return serverInstalling.get() || JobTracker.getInstance().hasActiveServerJob();
  }

  public CompletableFuture<Boolean> installServer(String serverType, String gameVersion, String loaderVersion, int ram,
      int job, Consumer<String> statusCallback) {
    CompletableFuture<Boolean> future = new CompletableFuture<>();
    serverInstalling.set(true);

    String jobId = "server-install-" + serverType.toLowerCase();
    final Thread[] workerThread = new Thread[1];
    final JobTracker.TrackedJob trackedJob = JobTracker.getInstance().registerJob(
        jobId,
        "Server Install: " + serverType + " (" + gameVersion + ")",
        "Server",
        () -> {
          serverInstalling.set(false);
          if (workerThread[0] != null) {
            workerThread[0].interrupt();
          }
        });
    trackedJob.addFileToCleanup(new File("server.jar.tmp"));
    trackedJob.addFileToCleanup(new File("fabric-installer.jar"));
    trackedJob.addFileToCleanup(new File("BuildTools.jar"));
    trackedJob.addFileToCleanup(new File("BuildTools.jar.tmp"));

    Consumer<JsonObject> handler = new Consumer<>() {
      @Override
      public void accept(JsonObject json) {
        if (trackedJob.isCancelled()) {
          removeListener(this);
          serverInstalling.set(false);
          JobTracker.getInstance().unregisterJob(jobId);
          future.completeExceptionally(new CancellationException("Server installation was cancelled"));
          return;
        }
        if (json.has("type") && "download".equals(json.get("type").getAsString())) {
          double prog = json.has("progress") ? json.get("progress").getAsDouble() : -1.0;
          String file = json.has("id") ? json.get("id").getAsString() : "server.jar";
          if (prog >= 0) {
            trackedJob.setProgress(prog);
            trackedJob.setStatus(String.format("Downloading %s (%.1f%%)", file, prog));
          }
          if (statusCallback != null) {
            if (prog >= 0) {
              statusCallback.accept(String.format("Downloading %s (%.2f%%)", file, prog));
            } else {
              statusCallback.accept(String.format("Downloading %s...", file));
            }
          }
        } else if (json.has("type") && "server".equals(json.get("type").getAsString())) {
          int status = json.has("status") ? json.get("status").getAsInt() : -1;
          if (status == 2) {
            trackedJob.setStatus("Installing/compiling components...");
            if (statusCallback != null) {
              statusCallback.accept("Installing server components...");
            }
          } else if (status == 3) {
            removeListener(this);
            serverInstalling.set(false);
            JobTracker.getInstance().unregisterJob(jobId);
            future.complete(true);
          }
        } else if (json.has("error")) {
          removeListener(this);
          serverInstalling.set(false);
          JobTracker.getInstance().unregisterJob(jobId);
          future.completeExceptionally(new RuntimeException(json.get("error").getAsString()));
        }
      }
    };

    addListener(handler);

    workerPool.submit(() -> {
      workerThread[0] = Thread.currentThread();
      try {
        String lVersion = (loaderVersion != null && !loaderVersion.isEmpty()) ? loaderVersion : "latest";
        ServerHandler sh = new ServerHandler("server", serverType.toLowerCase(), gameVersion, lVersion, ram, job);
        sh.portableJdk = ConfigManager.getInstance().getConfig().isPortableJdk();
        sh.serverHandler();
      } catch (Exception e) {
        serverInstalling.set(false);
        removeListener(handler);
        JobTracker.getInstance().unregisterJob(jobId);
        future.completeExceptionally(e);
      }
    });

    return future;
  }

  public static ProcessResult runShell(String... command) {
    try {
      ProcessBuilder pb = new ProcessBuilder(command);
      pb.redirectErrorStream(true);
      Process p = pb.start();
      StringBuilder sb = new StringBuilder();
      try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
        String line;
        while ((line = r.readLine()) != null) {
          sb.append(line).append("\n");
        }
      }
      int code = p.waitFor();
      return new ProcessResult(code, sb.toString().trim());
    } catch (Exception e) {
      return new ProcessResult(-1, e.getMessage());
    }
  }

  public static org.codeberg.DeployedReject.utils.ServerJarMetadata getInstalledServerMetadata() {
    TuiConfig cfg = ConfigManager.getInstance().getConfig();
    File sDir = new File(cfg.getServerDir() != null && !cfg.getServerDir().trim().isEmpty() ? cfg.getServerDir() : ".");
    return org.codeberg.DeployedReject.utils.ServerJarMetadata.readServerMetadata(sDir);
  }

  public static org.codeberg.DeployedReject.utils.ServerJarMetadata.CompatibilityResult checkCompatibility(String targetLoader, String targetGameVersion) {
    org.codeberg.DeployedReject.utils.ServerJarMetadata meta = getInstalledServerMetadata();
    return org.codeberg.DeployedReject.utils.ServerJarMetadata.checkCompatibility(meta, targetLoader, targetGameVersion);
  }

  public static boolean isServerInstalled() {
    if (isServerDownloading()) {
      return false;
    }
    if (new File("server.jar.tmp").exists() || new File("BuildTools.jar").exists()) {
      return false;
    }
    if (getInstalledServerMetadata() != null) {
      return true;
    }

    File fabricLaunch = new File("fabric-server-launch.jar");
    if (fabricLaunch.exists() && fabricLaunch.length() > 0) {
      File mcJar = new File("server.jar");
      File fabricMcJar = new File(".fabric", "server.jar");
      if ((mcJar.exists() && mcJar.length() > 5_000_000) ||
          (fabricMcJar.exists() && fabricMcJar.length() > 5_000_000)) {
        return true;
      }
    }

    File runSh = new File("run.sh");
    if (runSh.exists() && runSh.length() > 0) {
      return true;
    }

    File sJar = new File("server.jar");
    if (sJar.exists() && sJar.length() > 5_000_000) {
      return true;
    }
    return false;
  }

  public static boolean isServerRunning() {
    TuiConfig cfg = ConfigManager.getInstance().getConfig();
    return isServerRunning(cfg.getSessionName());
  }

  public static boolean isServerRunning(String sessionName) {
    String sess = (sessionName != null && !sessionName.trim().isEmpty()) ? sessionName.trim() : "mcsv";
    String mux = Platform.getMultiplexer();
    ProcessResult res = runShell(mux, "has-session", "-t", sess);
    if (res.exitCode == 0)
      return true;
    if ("mcsv".equals(sess)) {
      res = runShell(mux, "has-session", "-t", "mcServer");
      if (res.exitCode == 0)
        return true;
      if (!Platform.isWindows()) {
        res = runShell("pgrep", "-f", "server.jar");
        if (res.exitCode == 0)
          return true;
        res = runShell("pgrep", "-f", "fabric-server-launch.jar");
        return res.exitCode == 0;
      }
    }
    return false;
  }

  public static String getLiveConsoleOutput(int maxLines) {
    TuiConfig cfg = ConfigManager.getInstance().getConfig();
    return getLiveConsoleOutput(maxLines, cfg.getSessionName(), cfg.getServerDir());
  }

  public static String getLiveConsoleOutput(int maxLines, String sessionName, String serverDir) {
    String sess = (sessionName != null && !sessionName.trim().isEmpty()) ? sessionName.trim() : "mcsv";
    if (!isServerRunning(sess)) {
      return "[Server not started - Start server from Server Control [S] to view live output]";
    }
    String mux = Platform.getMultiplexer();
    ProcessResult res = runShell(mux, "capture-pane", "-t", sess, "-p", "-S", "-" + maxLines);
    if (res.exitCode == 0 && res.output != null && !res.output.trim().isEmpty()) {
      return res.output.trim();
    }
    File logFile = new File(serverDir != null && !serverDir.trim().isEmpty() ? serverDir : ".", "logs/latest.log");
    if (logFile.exists() && logFile.canRead()) {
      try {
        List<String> lines = Files.readAllLines(logFile.toPath(), StandardCharsets.UTF_8);
        int start = Math.max(0, lines.size() - maxLines);
        List<String> sub = lines.subList(start, lines.size());
        String out = String.join("\n", sub).trim();
        if (!out.isEmpty()) {
          return out;
        }
      } catch (Exception ignored) {
      }
    }
    return "[Server running - Waiting for console output...]";
  }

  public static boolean isCommandAvailable(String cmd) {
    return Platform.isCommandAvailable(cmd);
  }

  public static ProcessResult startServer(boolean publicTunnel) {
    return startServer(publicTunnel, "4G");
  }

  public static ProcessResult startServer(boolean publicTunnel, String ram) {
    TuiConfig cfg = ConfigManager.getInstance().getConfig();
    return startServer(publicTunnel, ram, cfg.getSessionName(), cfg.getServerDir());
  }

  public static ProcessResult startServer(boolean publicTunnel, String ram, String sessionName, String serverDir) {
    TuiConfig cfg = ConfigManager.getInstance().getConfig();
    boolean usePortable = cfg.isPortableJdk();
    String javaCmd = null;
    String jdkHome = null;
    if (usePortable) {
      int reqJdk = org.codeberg.DeployedReject.device.JdkManager.getRequiredJdkVersion(cfg.getGameVersion());
      if (org.codeberg.DeployedReject.device.JdkManager.isJdkInstalled(reqJdk)) {
        javaCmd = org.codeberg.DeployedReject.device.JdkManager.getJavaExecutable(reqJdk).getAbsolutePath();
        jdkHome = org.codeberg.DeployedReject.device.JdkManager.getJdkHome(reqJdk).getAbsolutePath();
      }
    }
    org.codeberg.DeployedReject.utils.ProcessResult res = ServerHandler.startServer(publicTunnel, ram, javaCmd, jdkHome, sessionName, serverDir);
    return new ProcessResult(res.exitCode, res.output);
  }

  public CompletableFuture<ModUpdateManager.UpdateSummary> updateAllMods(String gameVersion, String loader,
      Consumer<String> logCallback, Consumer<Double> progressCallback) {
    var compat = checkCompatibility(loader, gameVersion);
    if (!compat.isCompatible()) {
      log("[ERR:] " + compat.getMessage());
      CompletableFuture<ModUpdateManager.UpdateSummary> errFuture = new CompletableFuture<>();
      errFuture.completeExceptionally(new IllegalArgumentException(compat.getMessage()));
      return errFuture;
    }

    CompletableFuture<ModUpdateManager.UpdateSummary> future = new CompletableFuture<>();
    String jobId = "mods-update-all";

    final JobTracker.TrackedJob trackedJob = JobTracker.getInstance().registerJob(
        jobId,
        "Update All Mods (MC " + gameVersion + ")",
        "Mod",
        () -> future.cancel(true));

    workerPool.submit(() -> {
      try {
        ModUpdateManager.UpdateSummary summary = ModUpdateManager.updateAllMods(
            gameVersion, loader, email, curseAPI,
            msg -> {
              if (logCallback != null) logCallback.accept(msg);
              trackedJob.setStatus(msg);
            },
            pct -> {
              if (progressCallback != null) progressCallback.accept(pct);
              trackedJob.setProgress(pct);
            });
        JobTracker.getInstance().unregisterJob(jobId);
        future.complete(summary);
      } catch (Exception e) {
        JobTracker.getInstance().unregisterJob(jobId);
        future.completeExceptionally(e);
      }
    });

    return future;
  }

  public static ProcessResult stopServer() {
    TuiConfig cfg = ConfigManager.getInstance().getConfig();
    return stopServer(cfg.getSessionName());
  }

  public static ProcessResult stopServer(String sessionName) {
    org.codeberg.DeployedReject.utils.ProcessResult res = ServerHandler.stopServer(sessionName);
    return new ProcessResult(res.exitCode, res.output);
  }

  public static ProcessResult sendConsoleCommand(String cmd) {
    TuiConfig cfg = ConfigManager.getInstance().getConfig();
    return sendConsoleCommand(cfg.getSessionName(), cmd);
  }

  public static ProcessResult sendConsoleCommand(String sessionName, String cmd) {
    org.codeberg.DeployedReject.utils.ProcessResult res = ServerHandler.sendConsoleCommand(sessionName, cmd);
    return new ProcessResult(res.exitCode, res.output);
  }

  public static void setupTunnel() {
    org.codeberg.DeployedReject.device.Tunnel.setup();
  }

  public static void statusTunnel() {
    org.codeberg.DeployedReject.device.Tunnel.status();
  }

  public static void resetTunnel() {
    org.codeberg.DeployedReject.device.Tunnel.reset();
  }

  public static void startTunnelDaemon() {
    org.codeberg.DeployedReject.device.Tunnel.startDaemon();
  }

  public static void stopTunnelDaemon() {
    org.codeberg.DeployedReject.device.Tunnel.stopDaemon();
  }

  public static boolean isTunnelDaemonRunning() {
    return org.codeberg.DeployedReject.device.Tunnel.isDaemonRunning();
  }

  public static boolean isTunnelLinked() {
    File f = new File("playitagent.txt");
    return f.exists() && f.length() > 0;
  }

  public static List<TunnelInfo> getStoredTunnels() {
    List<TunnelInfo> list = new ArrayList<>();
    File f = new File("tunnels.json");
    if (!f.exists() || f.length() == 0) return list;
    try {
      String content = Files.readString(f.toPath(), StandardCharsets.UTF_8);
      JsonArray arr = com.google.gson.JsonParser.parseString(content).getAsJsonArray();
      for (JsonElement el : arr) {
        if (el.isJsonObject()) {
          JsonObject obj = el.getAsJsonObject();
          String id = obj.has("id") ? obj.get("id").getAsString() : "";
          String proto = obj.has("proto") ? obj.get("proto").getAsString() : "tcp";
          String pub = obj.has("publicAddress") ? obj.get("publicAddress").getAsString() : "";
          int port = obj.has("localPort") ? obj.get("localPort").getAsInt() : 25565;
          list.add(new TunnelInfo(id, proto, pub, port));
        }
      }
    } catch (Exception ignored) {}
    return list;
  }

  public static ProcessResult migratePlayer(String oldName, String newName) {
    TuiConfig cfg = ConfigManager.getInstance().getConfig();
    return migratePlayer(oldName, newName, cfg.getSessionName(), cfg.getServerDir());
  }

  public static ProcessResult migratePlayer(String oldName, String newName, String sessionName, String serverDir) {
    org.codeberg.DeployedReject.utils.ProcessResult res = MigrationHandler.migratePlayer(oldName, newName, sessionName, serverDir);
    return new ProcessResult(res.exitCode, res.output);
  }

  public static ProcessResult runBackup() {
    TuiConfig cfg = ConfigManager.getInstance().getConfig();
    return runBackup(cfg.getBackupSourceFolder(), cfg.getBackupTargetFolder(),
        cfg.getBackupRetentionLimit(), cfg.isBackupCloudSync(), cfg.getBackupCloudRemote(),
        cfg.getSessionName(), cfg.getServerDir());
  }

  public static ProcessResult runBackup(String sourceFolder, String targetFolder, int retentionLimit,
      boolean cloudSync, String cloudRemote) {
    TuiConfig cfg = ConfigManager.getInstance().getConfig();
    return runBackup(sourceFolder, targetFolder, retentionLimit, cloudSync, cloudRemote, cfg.getSessionName(), cfg.getServerDir());
  }

  public static ProcessResult runBackup(String sourceFolder, String targetFolder, int retentionLimit,
      boolean cloudSync, String cloudRemote, String sessionName, String serverDir) {
    org.codeberg.DeployedReject.utils.ProcessResult res = BackupHandler.runBackup(sourceFolder, targetFolder,
        retentionLimit, cloudSync, cloudRemote, sessionName, serverDir);
    return new ProcessResult(res.exitCode, res.output);
  }

  public static List<BackupInfo> listBackups() {
    TuiConfig cfg = ConfigManager.getInstance().getConfig();
    return listBackups(cfg.getBackupTargetFolder());
  }

  public static List<BackupInfo> listBackups(String targetFolder) {
    List<BackupInfo> list = new ArrayList<>();
    List<BackupHandler.BackupInfo> src = BackupHandler.listBackups(targetFolder);
    for (BackupHandler.BackupInfo b : src) {
      list.add(new BackupInfo(b.name, b.sizeBytes, b.lastModified));
    }
    return list;
  }

  public static List<String> listInstalledMods() {
    List<String> list = new ArrayList<>();
    File modsDir = new File("mods");
    if (!modsDir.exists() || !modsDir.isDirectory()) {
      return list;
    }
    File[] files = modsDir.listFiles((dir, name) -> name.endsWith(".jar"));
    if (files != null) {
      Arrays.sort(files, Comparator.comparing(File::getName));
      for (File f : files) {
        list.add(f.getName());
      }
    }
    return list;
  }

  public static boolean deleteMod(String filename) {
    File file = new File("mods", filename);
    if (file.exists()) {
      return file.delete();
    }
    return false;
  }

  public static boolean deleteBackup(String filename) {
    TuiConfig cfg = ConfigManager.getInstance().getConfig();
    return deleteBackup(cfg.getBackupTargetFolder(), filename);
  }

  public static boolean deleteBackup(String targetFolder, String filename) {
    return BackupHandler.deleteBackup(targetFolder, filename);
  }

  public static org.codeberg.DeployedReject.utils.ServerJarMetadata.CompatibilityResult checkPluginCompatibility(String serverPlatform, String gameVersion) {
    org.codeberg.DeployedReject.utils.ServerJarMetadata meta = getInstalledServerMetadata();
    return org.codeberg.DeployedReject.utils.ServerJarMetadata.checkPluginCompatibility(meta, serverPlatform, gameVersion);
  }

  public CompletableFuture<List<PluginResult>> searchPlugins(String platform, String query, String version, String serverPlatform) {
    CompletableFuture<List<PluginResult>> future = new CompletableFuture<>();
    workerPool.submit(() -> {
      try {
        List<PluginResult> list = new ArrayList<>();
        if ("curseForge".equalsIgnoreCase(platform) || "curseforge".equalsIgnoreCase(platform)) {
          String q = URLEncoder.encode(query != null ? query.trim() : "", StandardCharsets.UTF_8);
          String gVer = version != null && !"any".equalsIgnoreCase(version) ? URLEncoder.encode(version.trim(), StandardCharsets.UTF_8) : "";
          String url = "https://api.curseforge.com/v1/mods/search?gameId=432&classId=5&searchFilter=" + q;
          if (!gVer.isEmpty()) url += "&gameVersion=" + gVer;
          url += "&pageSize=15";
          HttpRequest req = HttpRequest.newBuilder()
              .uri(URI.create(url))
              .header("x-api-key", curseAPI != null ? curseAPI : "")
              .header("Accept", "application/json")
              .GET()
              .build();
          HttpResponse<String> resp = NetworkUtils.attemptS(req);
          if (resp != null && resp.statusCode() == 200) {
            JsonObject root = com.google.gson.JsonParser.parseString(resp.body()).getAsJsonObject();
            if (root.has("data") && root.get("data").isJsonArray()) {
              JsonArray arr = root.getAsJsonArray("data");
              for (JsonElement el : arr) {
                if (el.isJsonObject()) {
                  JsonObject o = el.getAsJsonObject();
                  String id = o.has("id") ? o.get("id").getAsString() : "";
                  String slug = o.has("slug") ? o.get("slug").getAsString() : id;
                  String name = o.has("name") ? o.get("name").getAsString() : "";
                  String author = "";
                  if (o.has("authors") && o.get("authors").isJsonArray() && o.getAsJsonArray("authors").size() > 0) {
                    JsonObject firstAuthor = o.getAsJsonArray("authors").get(0).getAsJsonObject();
                    if (firstAuthor.has("name") && !firstAuthor.get("name").isJsonNull()) {
                      author = firstAuthor.get("name").getAsString();
                    }
                  }
                  String summary = o.has("summary") && !o.get("summary").isJsonNull() ? o.get("summary").getAsString() : "";
                  int dls = o.has("downloadCount") ? o.get("downloadCount").getAsInt() : 0;
                  list.add(new PluginResult(id, slug, name, author, summary, dls, Collections.singletonList("bukkit")));
                }
              }
            }
          }
        } else {
          // Modrinth
          String encQuery = URLEncoder.encode(query != null ? query.trim() : "", StandardCharsets.UTF_8);
          String facets;
          if (version != null && !version.isEmpty() && !"any".equalsIgnoreCase(version)) {
            facets = "[[\"project_type:plugin\"],[\"versions:" + version + "\"]]";
          } else {
            facets = "[[\"project_type:plugin\"]]";
          }
          String encFacets = URLEncoder.encode(facets, StandardCharsets.UTF_8);
          String url = "https://api.modrinth.com/v2/search?query=" + encQuery + "&facets=" + encFacets + "&limit=15";
          HttpRequest req = HttpRequest.newBuilder()
              .uri(URI.create(url))
              .header("User-Agent", "DeployedReject/MurCes/1.6.0 (" + email + ")")
              .GET()
              .build();
          HttpResponse<String> resp = NetworkUtils.attemptS(req);
          if (resp != null && resp.statusCode() == 200) {
            JsonObject root = com.google.gson.JsonParser.parseString(resp.body()).getAsJsonObject();
            if (root.has("hits") && root.get("hits").isJsonArray()) {
              JsonArray arr = root.getAsJsonArray("hits");
              for (JsonElement el : arr) {
                if (el.isJsonObject()) {
                  JsonObject o = el.getAsJsonObject();
                  String id = o.has("project_id") ? o.get("project_id").getAsString() : "";
                  String slug = o.has("slug") && !o.get("slug").isJsonNull() ? o.get("slug").getAsString() : id;
                  String name = o.has("title") && !o.get("title").isJsonNull() ? o.get("title").getAsString() : slug;
                  String author = o.has("author") && !o.get("author").isJsonNull() ? o.get("author").getAsString() : "";
                  String desc = o.has("description") && !o.get("description").isJsonNull() ? o.get("description").getAsString() : "";
                  int dls = o.has("downloads") ? o.get("downloads").getAsInt() : 0;
                  List<String> cats = new ArrayList<>();
                  if (o.has("categories") && o.get("categories").isJsonArray()) {
                    for (JsonElement ce : o.getAsJsonArray("categories")) {
                      cats.add(ce.getAsString());
                    }
                  }
                  list.add(new PluginResult(id, slug, name, author, desc, dls, cats));
                }
              }
            }
          }
        }
        future.complete(list);
      } catch (Exception e) {
        future.completeExceptionally(e);
      }
    });
    return future;
  }

  public CompletableFuture<List<PluginVersionInfo>> getPluginVersions(String platform, String pluginIdOrSlug,
      String gameVersion, String serverPlatform) {
    CompletableFuture<List<PluginVersionInfo>> future = new CompletableFuture<>();
    workerPool.submit(() -> {
      try {
        List<PluginVersionInfo> list = new ArrayList<>();
        if ("curseForge".equalsIgnoreCase(platform) || "curseforge".equalsIgnoreCase(platform)) {
          String url = "https://api.curseforge.com/v1/mods/" + pluginIdOrSlug + "/files";
          if (gameVersion != null && !gameVersion.isEmpty() && !"any".equalsIgnoreCase(gameVersion)) {
            url += "?gameVersion=" + URLEncoder.encode(gameVersion.trim(), StandardCharsets.UTF_8);
          }
          HttpRequest req = HttpRequest.newBuilder()
              .uri(URI.create(url))
              .header("x-api-key", curseAPI != null ? curseAPI : "")
              .header("Accept", "application/json")
              .GET()
              .build();
          HttpResponse<String> resp = NetworkUtils.attemptS(req);
          if (resp != null && resp.statusCode() == 200) {
            JsonObject root = com.google.gson.JsonParser.parseString(resp.body()).getAsJsonObject();
            if (root.has("data") && root.get("data").isJsonArray()) {
              JsonArray data = root.getAsJsonArray("data");
              for (JsonElement el : data) {
                if (el.isJsonObject()) {
                  JsonObject obj = el.getAsJsonObject();
                  String vId = obj.get("id").getAsString();
                  String vName = obj.has("displayName") ? obj.get("displayName").getAsString() : "";
                  String fn = obj.has("fileName") ? obj.get("fileName").getAsString() : "";
                  String dUrl = (obj.has("downloadUrl") && !obj.get("downloadUrl").isJsonNull())
                      ? obj.get("downloadUrl").getAsString() : "";
                  long sz = obj.has("fileLength") ? obj.get("fileLength").getAsLong() : -1;
                  list.add(new PluginVersionInfo(vId, vName, fn, dUrl, fn, sz));
                }
              }
            }
          }
        } else {
          // Modrinth
          String url = "https://api.modrinth.com/v2/project/" + pluginIdOrSlug + "/version";
          HttpRequest req = HttpRequest.newBuilder()
              .uri(URI.create(url))
              .header("User-Agent", "DeployedReject/MurCes/1.6.0 (" + email + ")")
              .GET()
              .build();
          HttpResponse<String> resp = NetworkUtils.attemptS(req);
          if (resp != null && resp.statusCode() == 200) {
            JsonArray arr = com.google.gson.JsonParser.parseString(resp.body()).getAsJsonArray();
            for (JsonElement el : arr) {
              if (el.isJsonObject()) {
                JsonObject obj = el.getAsJsonObject();
                String vId = obj.get("id").getAsString();
                String vName = obj.has("name") ? obj.get("name").getAsString() : "";
                String vNum = obj.has("version_number") ? obj.get("version_number").getAsString() : "";
                String dUrl = "";
                String fn = "";
                long sz = -1;
                if (obj.has("files") && obj.getAsJsonArray("files").size() > 0) {
                  for (JsonElement fe : obj.getAsJsonArray("files")) {
                    if (fe.isJsonObject()) {
                      JsonObject fo = fe.getAsJsonObject();
                      boolean isPrimary = fo.has("primary") && fo.get("primary").getAsBoolean();
                      String fname = fo.has("filename") ? fo.get("filename").getAsString() : "";
                      if (isPrimary || fname.endsWith(".jar") || dUrl.isEmpty()) {
                        dUrl = fo.has("url") ? fo.get("url").getAsString() : "";
                        fn = fname;
                        sz = fo.has("size") ? fo.get("size").getAsLong() : -1;
                        if (isPrimary || fname.endsWith(".jar")) break;
                      }
                    }
                  }
                }
                list.add(new PluginVersionInfo(vId, vName, vNum, dUrl, fn, sz));
              }
            }
          }
        }
        future.complete(list);
      } catch (Exception e) {
        future.completeExceptionally(e);
      }
    });
    return future;
  }

  public CompletableFuture<String> getPluginFullDescription(String platform, String pluginIdOrSlug) {
    return getModFullDescription(platform, pluginIdOrSlug);
  }

  public CompletableFuture<Boolean> downloadPluginDirect(String downloadUrl, String filename,
      String serverPlatform, String gameVersion,
      Consumer<DownloadProgressInfo> richProgressCallback) {

    var compat = checkPluginCompatibility(serverPlatform, gameVersion);
    if (!compat.isCompatible()) {
      log("[ERR:] " + compat.getMessage());
      CompletableFuture<Boolean> errFuture = new CompletableFuture<>();
      errFuture.completeExceptionally(new IllegalArgumentException(compat.getMessage()));
      return errFuture;
    }

    CompletableFuture<Boolean> future = new CompletableFuture<>();
    String jobId = "plugin-direct-" + filename;
    File pluginsDir = new File("plugins");
    if (!pluginsDir.exists()) {
      pluginsDir.mkdirs();
    }
    File targetTmp = new File(pluginsDir, filename + ".tmp");
    File targetFinal = new File(pluginsDir, filename);

    final Thread[] workerThread = new Thread[1];
    final JobTracker.TrackedJob trackedJob = JobTracker.getInstance().registerJob(
        jobId,
        "Download Plugin: " + filename,
        "Plugin",
        () -> {
          if (workerThread[0] != null) {
            workerThread[0].interrupt();
          }
          if (targetTmp.exists()) {
            try {
              targetTmp.delete();
            } catch (Exception ignored) {}
          }
        });
    trackedJob.addFileToCleanup(targetTmp);

    workerPool.submit(() -> {
      workerThread[0] = Thread.currentThread();
      boolean finishedSuccessfully = false;
      try {
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(downloadUrl))
            .header("User-Agent", "DeployedReject/MurCes/1.6.0 (" + email + ")")
            .GET()
            .build();
        HttpResponse<InputStream> resp = NetworkUtils.attemptI(req);
        if (resp == null || resp.statusCode() != 200) {
          JobTracker.getInstance().unregisterJob(jobId);
          future.completeExceptionally(
              new IOException("Failed to download plugin file: HTTP " + (resp != null ? resp.statusCode() : "null")));
          return;
        }
        long filesize = resp.headers().firstValueAsLong("content-length").orElse(-1L);
        long startTime = System.currentTimeMillis();

        try (InputStream in = resp.body(); FileOutputStream out = new FileOutputStream(targetTmp)) {
          byte[] buf = new byte[8192];
          int n;
          long totalRead = 0;
          long lastCallback = 0;
          while ((n = in.read(buf)) != -1) {
            if (Thread.currentThread().isInterrupted() || trackedJob.isCancelled()) {
              throw new InterruptedException("Plugin download cancelled");
            }
            out.write(buf, 0, n);
            totalRead += n;
            long now = System.currentTimeMillis();
            if (now - lastCallback >= 500 || (filesize > 0 && totalRead == filesize)) {
              lastCallback = now;
              double elapsedSec = Math.max(0.001, (now - startTime) / 1000.0);
              double speedMBps = (totalRead / (1024.0 * 1024.0)) / elapsedSec;
              int eta = (filesize > totalRead && speedMBps > 0)
                  ? (int) Math.round(((filesize - totalRead) / (1024.0 * 1024.0)) / speedMBps)
                  : 0;
              double pct = filesize > 0 ? (totalRead * 100.0) / filesize : -1.0;
              if (pct >= 0) {
                trackedJob.setProgress(pct);
                trackedJob.setStatus(String.format("%.1f%% (ETA: %ds @ %.1f MB/s)", pct, eta, speedMBps));
              }
              if (richProgressCallback != null) {
                richProgressCallback.accept(new DownloadProgressInfo(pct, totalRead, filesize, speedMBps, eta));
              }
            }
          }
          out.flush();
        }

        try {
          Files.move(targetTmp.toPath(), targetFinal.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING,
              java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception moveEx) {
          Files.move(targetTmp.toPath(), targetFinal.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }

        finishedSuccessfully = true;
        JobTracker.getInstance().unregisterJob(jobId);
        future.complete(true);
      } catch (Exception e) {
        if (!finishedSuccessfully && targetTmp.exists()) {
          try {
            targetTmp.delete();
          } catch (Exception ignored) {}
        }
        JobTracker.getInstance().unregisterJob(jobId);
        future.completeExceptionally(e);
      }
    });
    return future;
  }

  public static PluginFileInfo parsePluginJar(File jarFile) {
    if (jarFile == null || !jarFile.exists() || jarFile.length() < 100) return null;
    String name = jarFile.getName();
    String version = "";
    String author = "";
    String desc = "";

    try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(jarFile)) {
      java.util.zip.ZipEntry ze = zip.getEntry("plugin.yml");
      if (ze == null) ze = zip.getEntry("paper-plugin.yml");
      if (ze != null) {
        try (InputStream in = zip.getInputStream(ze);
             BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
          String line;
          while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (line.startsWith("#") || line.isEmpty()) continue;
            if (line.startsWith("name:")) {
              name = line.substring("name:".length()).trim().replace("\"", "").replace("'", "");
            } else if (line.startsWith("version:")) {
              version = line.substring("version:".length()).trim().replace("\"", "").replace("'", "");
            } else if (line.startsWith("author:")) {
              author = line.substring("author:".length()).trim().replace("\"", "").replace("'", "");
            } else if (line.startsWith("description:")) {
              desc = line.substring("description:".length()).trim().replace("\"", "").replace("'", "");
            }
          }
        }
      }
    } catch (Exception ignored) {}

    if (version.isEmpty()) {
      java.util.regex.Matcher m = java.util.regex.Pattern.compile("^([a-zA-Z0-9_.-]+?)-([vV]?\\d[a-zA-Z0-9_.+-]*)\\.jar$").matcher(jarFile.getName());
      if (m.matches()) {
        name = m.group(1);
        version = m.group(2);
      }
    }
    return new PluginFileInfo(jarFile.getName(), name, version, author, desc, jarFile.length(), jarFile.lastModified());
  }

  public static List<PluginFileInfo> listInstalledPlugins() {
    List<PluginFileInfo> list = new ArrayList<>();
    File pluginsDir = new File("plugins");
    if (!pluginsDir.exists() || !pluginsDir.isDirectory()) {
      return list;
    }
    File[] files = pluginsDir.listFiles((dir, name) -> name.endsWith(".jar") && !name.endsWith(".tmp"));
    if (files != null) {
      Arrays.sort(files, Comparator.comparing(File::getName));
      for (File f : files) {
        PluginFileInfo info = parsePluginJar(f);
        if (info != null) {
          list.add(info);
        }
      }
    }
    return list;
  }

  public static boolean deletePlugin(String filename) {
    File file = new File("plugins", filename);
    if (file.exists()) {
      return file.delete();
    }
    return false;
  }
}
