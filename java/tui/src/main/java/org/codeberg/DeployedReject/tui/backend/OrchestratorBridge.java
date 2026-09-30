package org.codeberg.DeployedReject.tui.backend;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.codeberg.DeployedReject.device.ServerHandler;
import org.codeberg.DeployedReject.mods.CurseForge;
import org.codeberg.DeployedReject.mods.Modrinth;
import org.codeberg.DeployedReject.utils.Communicator;

import java.io.*;
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

        public ModResult(String id, String name) {
            this.id = id;
            this.name = name;
        }

        @Override
        public String toString() {
            return name + " (" + id + ")";
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
    private final ExecutorService workerPool = Executors.newCachedThreadPool();
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
                                results.add(new ModResult(id, name));
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

    public CompletableFuture<Boolean> downloadMod(String platform, String modIdOrSlug, String version, String loader, Consumer<Integer> progressCallback) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();

        Consumer<JsonObject> handler = new Consumer<>() {
            @Override
            public void accept(JsonObject json) {
                if (json.has("type") && "download".equals(json.get("type").getAsString())) {
                    int status = json.has("status") ? json.get("status").getAsInt() : -1;
                    if (json.has("progress") && progressCallback != null) {
                        progressCallback.accept(json.get("progress").getAsInt());
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

    public CompletableFuture<Boolean> installServer(String serverType, String gameVersion, String loaderVersion, int ram, int job, Consumer<String> statusCallback) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();

        Consumer<JsonObject> handler = new Consumer<>() {
            @Override
            public void accept(JsonObject json) {
                if (json.has("type") && "server".equals(json.get("type").getAsString())) {
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

    public static boolean isServerRunning() {
        ProcessResult res = runShell("tmux", "has-session", "-t", "mcsv");
        if (res.exitCode == 0) return true;
        res = runShell("tmux", "has-session", "-t", "mcServer");
        if (res.exitCode == 0) return true;
        res = runShell("pgrep", "-f", "server.jar");
        return res.exitCode == 0;
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
