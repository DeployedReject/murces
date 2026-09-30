package org.codeberg.DeployedReject.tui.backend;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.codeberg.DeployedReject.device.ServerHandler;
import org.codeberg.DeployedReject.mods.CurseForge;
import org.codeberg.DeployedReject.mods.Modrinth;
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

        public ModVersionInfo(String versionId, String versionName, String versionNumber, String downloadUrl, String filename, long sizeBytes) {
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
            if (etaSeconds <= 0) return "calculating...";
            if (etaSeconds < 60) return etaSeconds + "s";
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
            if (sizeBytes < 1024) return sizeBytes + " B";
            int exp = (int) (Math.log(sizeBytes) / Math.log(1024));
            char unit = "KMGTPE".charAt(exp - 1);
            return String.format("%.1f %cB", sizeBytes / Math.pow(1024, exp), unit);
        }

        public String formattedDate() {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
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
    private String curseAPI = "";
    private String email = "user@murces.local";

    private OrchestratorBridge() {
        try {
            if (!Files.isDirectory(Paths.get("mods"))) {
                Files.createDirectories(Paths.get("mods"));
            }
        } catch (Exception ignored) {}

        loadConfig();
        startQueueConsumer();
    }

    private void loadConfig() {
        // 1. Try config.properties from classpath / packaged resources
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
        } catch (Exception ignored) {}

        // 2. Try loading .env file from working directory or parent directory
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
                        if (line.isEmpty() || line.startsWith("#")) continue;
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
                } catch (Exception ignored) {}
                break;
            }
        }

        // 3. System environment variables take highest precedence
        if (System.getenv("curseAPI") != null && !System.getenv("curseAPI").isEmpty()) {
            curseAPI = System.getenv("curseAPI");
        }
        if (System.getenv("email") != null && !System.getenv("email").isEmpty()) {
            email = System.getenv("email");
        }

        // Sanitize any unresolved Maven placeholder
        if (curseAPI != null && (curseAPI.startsWith("${") || curseAPI.isEmpty())) {
            curseAPI = "";
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
                        } catch (Exception ignored) {}
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
            } catch (Exception ignored) {}
        }
    }

    public void stopOrchestrator() {
        workerPool.shutdownNow();
    }

    // --- High-level in-memory Orchestrator operations ---

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

        // Timeout fallback after 15s
        Executors.newSingleThreadScheduledExecutor().schedule(() -> {
            if (!future.isDone()) {
                removeListener(handler);
                future.completeExceptionally(new TimeoutException("Mod search timed out"));
            }
        }, 15, TimeUnit.SECONDS);

        return future;
    }

    public CompletableFuture<List<ModVersionInfo>> getModVersions(String platform, String modIdOrSlug, String gameVersion, String loader) {
        CompletableFuture<List<ModVersionInfo>> future = new CompletableFuture<>();
        workerPool.submit(() -> {
            try {
                List<ModVersionInfo> list = new ArrayList<>();
                if (!"curseForge".equalsIgnoreCase(platform) && !"curseforge".equalsIgnoreCase(platform)) {
                    String encLoader = URLEncoder.encode("[\"" + loader + "\"]", StandardCharsets.UTF_8);
                    String encVersion = URLEncoder.encode("[\"" + gameVersion + "\"]", StandardCharsets.UTF_8);
                    String url = "https://api.modrinth.com/v2/project/" + modIdOrSlug + "/version?loaders=" + encLoader + "&game_versions=" + encVersion;
                    HttpRequest req = HttpRequest.newBuilder()
                            .uri(URI.create(url))
                            .header("User-Agent", "DeployedReject/MurCes/0.1 (" + email + ")")
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
                    int loaderType = 4; // fabric default
                    if ("forge".equalsIgnoreCase(loader)) loaderType = 1;
                    else if ("cauldron".equalsIgnoreCase(loader)) loaderType = 2;
                    else if ("liteLoader".equalsIgnoreCase(loader)) loaderType = 3;
                    else if ("quilt".equalsIgnoreCase(loader)) loaderType = 5;
                    else if ("neoForge".equalsIgnoreCase(loader)) loaderType = 6;

                    String url = "https://api.curseforge.com/v1/mods/" + modIdOrSlug + "/files?gameVersion=" + gameVersion + "&modLoaderType=" + loaderType;
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
                                    String dUrl = (obj.has("downloadUrl") && !obj.get("downloadUrl").isJsonNull()) ? obj.get("downloadUrl").getAsString() : "";
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

    public CompletableFuture<Boolean> downloadMod(String platform, String modIdOrSlug, String version, String loader, Consumer<Double> progressCallback) {
        return downloadMod(platform, modIdOrSlug, version, loader, progressCallback, null);
    }

    public CompletableFuture<Boolean> downloadMod(String platform, String modIdOrSlug, String version, String loader,
                                                  Consumer<Double> progressCallback,
                                                  Consumer<DownloadProgressInfo> richProgressCallback) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        long startTime = System.currentTimeMillis();

        Consumer<JsonObject> handler = new Consumer<>() {
            @Override
            public void accept(JsonObject json) {
                if (json.has("type") && "download".equals(json.get("type").getAsString())) {
                    int status = json.has("status") ? json.get("status").getAsInt() : -1;
                    if (json.has("progress")) {
                        double pct = json.get("progress").getAsDouble();
                        if (progressCallback != null) {
                            progressCallback.accept(pct);
                        }
                        if (richProgressCallback != null) {
                            long read = json.has("read") ? json.get("read").getAsLong() : 0L;
                            long total = json.has("total") ? json.get("total").getAsLong() : 0L;
                            long now = System.currentTimeMillis();
                            double elapsedSec = Math.max(0.001, (now - startTime) / 1000.0);
                            double speedMBps = (read / (1024.0 * 1024.0)) / elapsedSec;
                            int eta = (total > read && speedMBps > 0) ? (int) Math.round(((total - read) / (1024.0 * 1024.0)) / speedMBps) : 0;
                            richProgressCallback.accept(new DownloadProgressInfo(pct, read, total, speedMBps, eta));
                        }
                    }
                    if (status == 3) {
                        removeListener(this);
                        future.complete(true);
                    }
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
                    CurseForge cf = new CurseForge("download", modIdOrSlug, version, loader, curseAPI, modIdOrSlug);
                    cf.handler();
                } else {
                    Modrinth m = new Modrinth("download", modIdOrSlug, version, loader, email);
                    m.handler();
                }
            } catch (Exception e) {
                removeListener(handler);
                future.completeExceptionally(e);
            }
        });

        return future;
    }

    public CompletableFuture<Boolean> downloadModDirect(String downloadUrl, String filename, Consumer<DownloadProgressInfo> richProgressCallback) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        workerPool.submit(() -> {
            try {
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(downloadUrl))
                        .header("User-Agent", "DeployedReject/MurCes/0.1 (" + email + ")")
                        .GET()
                        .build();
                HttpResponse<InputStream> resp = NetworkUtils.attemptI(req);
                if (resp == null || resp.statusCode() != 200) {
                    future.completeExceptionally(new IOException("Failed to download mod file: HTTP " + (resp != null ? resp.statusCode() : "null")));
                    return;
                }
                long filesize = resp.headers().firstValueAsLong("content-length").orElse(-1L);
                long startTime = System.currentTimeMillis();
                File target = new File("mods", filename);
                try (InputStream in = resp.body(); FileOutputStream out = new FileOutputStream(target)) {
                    byte[] buf = new byte[8192];
                    int n;
                    long totalRead = 0;
                    long lastCallback = 0;
                    while ((n = in.read(buf)) != -1) {
                        out.write(buf, 0, n);
                        totalRead += n;
                        long now = System.currentTimeMillis();
                        if (now - lastCallback >= 500 || (filesize > 0 && totalRead == filesize)) {
                            lastCallback = now;
                            double elapsedSec = Math.max(0.001, (now - startTime) / 1000.0);
                            double speedMBps = (totalRead / (1024.0 * 1024.0)) / elapsedSec;
                            int eta = (filesize > totalRead && speedMBps > 0) ? (int) Math.round(((filesize - totalRead) / (1024.0 * 1024.0)) / speedMBps) : 0;
                            double pct = filesize > 0 ? (totalRead * 100.0) / filesize : 0.0;
                            if (richProgressCallback != null) {
                                richProgressCallback.accept(new DownloadProgressInfo(pct, totalRead, filesize, speedMBps, eta));
                            }
                        }
                    }
                }
                future.complete(true);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    public CompletableFuture<Boolean> installServer(String serverType, String gameVersion, String loaderVersion, int ram, int job, Consumer<String> statusCallback) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();

        Consumer<JsonObject> handler = new Consumer<>() {
            @Override
            public void accept(JsonObject json) {
                if (json.has("type") && "download".equals(json.get("type").getAsString())) {
                    double prog = json.has("progress") ? json.get("progress").getAsDouble() : -1.0;
                    String file = json.has("id") ? json.get("id").getAsString() : "server.jar";
                    if (statusCallback != null && prog >= 0) {
                        statusCallback.accept(String.format("Downloading %s (%.2f%%)", file, prog));
                    }
                } else if (json.has("type") && "server".equals(json.get("type").getAsString())) {
                    int status = json.has("status") ? json.get("status").getAsInt() : -1;
                    if (statusCallback != null) {
                        statusCallback.accept("Server job status: " + status);
                    }
                    if (status == 3) {
                        removeListener(this);
                        future.complete(true);
                    }
                } else if (json.has("error")) {
                    removeListener(this);
                    future.completeExceptionally(new RuntimeException(json.get("error").getAsString()));
                }
            }
        };

        addListener(handler);

        workerPool.submit(() -> {
            try {
                String lVersion = (loaderVersion != null && !loaderVersion.isEmpty()) ? loaderVersion : "latest";
                ServerHandler sh = new ServerHandler("server", serverType.toLowerCase(), gameVersion, lVersion, ram, job);
                sh.serverHandler();
            } catch (Exception e) {
                removeListener(handler);
                future.completeExceptionally(e);
            }
        });

        return future;
    }

    // --- System Shell Operations ---

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
        return new File("server.jar").exists() ||
               new File("run.sh").exists() ||
               new File("sv_start.sh").exists() ||
               new File("fabric-server-launch.jar").exists();
    }

    public static boolean isServerRunning() {
        ProcessResult res = runShell("tmux", "has-session", "-t", "mcsv");
        if (res.exitCode == 0) return true;
        res = runShell("tmux", "has-session", "-t", "mcServer");
        if (res.exitCode == 0) return true;
        res = runShell("pgrep", "-f", "server.jar");
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

    public static ProcessResult startServer(boolean publicTunnel) {
        String script = findScript("c/Shell/svctrl.sh", "./svctrl.sh");
        if (publicTunnel) {
            return runShell("bash", script, "start", "--public");
        } else {
            return runShell("bash", script, "start");
        }
    }

    public static ProcessResult stopServer() {
        String script = findScript("c/Shell/svctrl.sh", "./svctrl.sh");
        return runShell("bash", script, "stop");
    }

    public static ProcessResult sendConsoleCommand(String cmd) {
        String script = findScript("c/Shell/svctrl.sh", "./svctrl.sh");
        return runShell("bash", script, "-mc", cmd);
    }

    public static ProcessResult migratePlayer(String oldName, String newName) {
        String script = findScript("c/Shell/svctrl.sh", "./svctrl.sh");
        return runShell("bash", script, "--migrate", oldName, newName);
    }

    public static ProcessResult runBackup() {
        String script = findScript("c/Shell/backup.sh", "./backup.sh");
        return runShell("bash", script);
    }

    public static List<BackupInfo> listBackups() {
        List<BackupInfo> list = new ArrayList<>();
        File backupDir = new File("backup");
        if (!backupDir.exists() || !backupDir.isDirectory()) {
            return list;
        }
        File[] files = backupDir.listFiles((dir, name) -> name.endsWith(".tar") || name.endsWith(".tar.gz"));
        if (files != null) {
            Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
            for (File f : files) {
                list.add(new BackupInfo(f.getName(), f.length(), f.lastModified()));
            }
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

    private static String findScript(String relative1, String relative2) {
        if (new File(relative1).exists()) return relative1;
        if (new File(relative2).exists()) return relative2;
        return relative1;
    }
}
