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
              .header("User-Agent", "DeployedReject/MurCes/1.2.0 (" + email + ")")
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
              .header("User-Agent", "DeployedReject/MurCes/1.2.0 (" + email + ")")
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
            .header("User-Agent", "DeployedReject/MurCes/1.2.0 (" + email + ")")
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

  public static boolean isServerInstalled() {
    if (isServerDownloading()) {
      return false;
    }
    if (new File("server.jar.tmp").exists() || new File("BuildTools.jar").exists()) {
      return false;
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
    ProcessResult res = runShell("tmux", "has-session", "-t", "mcsv");
    if (res.exitCode == 0)
      return true;
    res = runShell("tmux", "has-session", "-t", "mcServer");
    if (res.exitCode == 0)
      return true;
    res = runShell("pgrep", "-f", "server.jar");
    if (res.exitCode == 0)
      return true;
    res = runShell("pgrep", "-f", "fabric-server-launch.jar");
    return res.exitCode == 0;
  }

  public static String getLiveConsoleOutput(int maxLines) {
    if (!isServerRunning()) {
      return "[Server not started - Start server from Server Control [S] to view live output]";
    }
    ProcessResult res = runShell("tmux", "capture-pane", "-t", "mcsv", "-p", "-S", "-" + maxLines);
    if (res.exitCode == 0 && res.output != null && !res.output.trim().isEmpty()) {
      return res.output.trim();
    }
    File logFile = new File("logs/latest.log");
    if (logFile.exists() && logFile.canRead()) {
      ProcessResult tailRes = runShell("tail", "-n", String.valueOf(maxLines), "logs/latest.log");
      if (tailRes.exitCode == 0 && tailRes.output != null && !tailRes.output.trim().isEmpty()) {
        return tailRes.output.trim();
      }
    }
    return "[Server running - Waiting for console output...]";
  }

  public static boolean isCommandAvailable(String cmd) {
    try {
      Process p = new ProcessBuilder("which", cmd).start();
      return p.waitFor() == 0;
    } catch (Exception e) {
      return false;
    }
  }

  public static ProcessResult startServer(boolean publicTunnel) {
    return startServer(publicTunnel, "4G");
  }

  public static ProcessResult startServer(boolean publicTunnel, String ram) {
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
    org.codeberg.DeployedReject.utils.ProcessResult res = ServerHandler.startServer(publicTunnel, ram, javaCmd, jdkHome);
    return new ProcessResult(res.exitCode, res.output);
  }

  public CompletableFuture<ModUpdateManager.UpdateSummary> updateAllMods(String gameVersion, String loader,
      Consumer<String> logCallback, Consumer<Double> progressCallback) {
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
    org.codeberg.DeployedReject.utils.ProcessResult res = ServerHandler.stopServer();
    return new ProcessResult(res.exitCode, res.output);
  }

  public static ProcessResult sendConsoleCommand(String cmd) {
    org.codeberg.DeployedReject.utils.ProcessResult res = ServerHandler.sendConsoleCommand(cmd);
    return new ProcessResult(res.exitCode, res.output);
  }

  public static ProcessResult migratePlayer(String oldName, String newName) {
    org.codeberg.DeployedReject.utils.ProcessResult res = MigrationHandler.migratePlayer(oldName, newName);
    return new ProcessResult(res.exitCode, res.output);
  }

  public static ProcessResult runBackup() {
    TuiConfig cfg = ConfigManager.getInstance().getConfig();
    return runBackup(cfg.getBackupSourceFolder(), cfg.getBackupTargetFolder(),
        cfg.getBackupRetentionLimit(), cfg.isBackupCloudSync(), cfg.getBackupCloudRemote());
  }

  public static ProcessResult runBackup(String sourceFolder, String targetFolder, int retentionLimit,
      boolean cloudSync, String cloudRemote) {
    org.codeberg.DeployedReject.utils.ProcessResult res = BackupHandler.runBackup(sourceFolder, targetFolder,
        retentionLimit, cloudSync, cloudRemote);
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
}
