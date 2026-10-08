package org.codeberg.DeployedReject.tui.backend;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.codeberg.DeployedReject.Main;
import org.codeberg.DeployedReject.tui.config.ConfigManager;
import org.codeberg.DeployedReject.tui.config.TuiConfig;
import org.codeberg.DeployedReject.utils.NetworkUtils;

import java.io.*;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ModUpdateManager {

  private static final Pattern FILENAME_PATTERN =
      Pattern.compile("^([a-zA-Z0-9_.-]+?)-([vV]?\\d[a-zA-Z0-9_.+-]*)\\.jar$");

  public enum UpdateStatus {
    UPGRADED,
    ALREADY_LATEST,
    NOT_FOUND,
    ERROR,
    SKIPPED
  }

  public static class ModFileInfo {
    public final File file;
    public final String slug;
    public final String version;
    public final String rawName;

    public ModFileInfo(File file, String slug, String version) {
      this.file = file;
      this.slug = slug != null ? slug.toLowerCase().trim() : "";
      this.version = version != null ? version.trim() : "0.0.0";
      this.rawName = file.getName();
    }

    @Override
    public String toString() {
      return slug + " (v" + version + ") [" + rawName + "]";
    }
  }

  public static class UpdateResult {
    public final ModFileInfo originalMod;
    public final String newVersion;
    public final String newFilename;
    public final UpdateStatus status;
    public final String message;

    public UpdateResult(ModFileInfo originalMod, String newVersion, String newFilename,
                        UpdateStatus status, String message) {
      this.originalMod = originalMod;
      this.newVersion = newVersion;
      this.newFilename = newFilename;
      this.status = status;
      this.message = message;
    }
  }

  public static class UpdateSummary {
    public int total = 0;
    public int upgraded = 0;
    public int alreadyUpToDate = 0;
    public int notFound = 0;
    public int errors = 0;
    public final List<UpdateResult> results = new ArrayList<>();

    public String formattedSummary() {
      return String.format("Processed %d mods: %d upgraded, %d already up to date, %d not found/skipped, %d errors.",
          total, upgraded, alreadyUpToDate, notFound, errors);
    }
  }

  public static ModFileInfo parseModFile(File file) {
    if (file == null || !file.exists() || !file.getName().endsWith(".jar")) {
      return null;
    }

    String filename = file.getName();
    String slug = null;
    String version = null;

    try (ZipFile zip = new ZipFile(file)) {
      ZipEntry fabricEntry = zip.getEntry("fabric.mod.json");
      if (fabricEntry != null) {
        try (InputStream in = zip.getInputStream(fabricEntry);
             InputStreamReader isr = new InputStreamReader(in, StandardCharsets.UTF_8)) {
          JsonObject json = JsonParser.parseReader(isr).getAsJsonObject();
          if (json.has("id") && !json.get("id").isJsonNull()) {
            slug = json.get("id").getAsString();
          }
          if (json.has("version") && !json.get("version").isJsonNull()) {
            version = json.get("version").getAsString();
          }
        }
      }

      if (slug == null) {
        ZipEntry quiltEntry = zip.getEntry("quilt.mod.json");
        if (quiltEntry != null) {
          try (InputStream in = zip.getInputStream(quiltEntry);
               InputStreamReader isr = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            JsonObject json = JsonParser.parseReader(isr).getAsJsonObject();
            if (json.has("quilt_loader") && json.getAsJsonObject("quilt_loader").has("id")) {
              slug = json.getAsJsonObject("quilt_loader").get("id").getAsString();
            }
            if (json.has("quilt_loader") && json.getAsJsonObject("quilt_loader").has("version")) {
              version = json.getAsJsonObject("quilt_loader").get("version").getAsString();
            }
          }
        }
      }

      if (slug == null) {
        ZipEntry tomlEntry = zip.getEntry("META-INF/mods.toml");
        if (tomlEntry != null) {
          try (InputStream in = zip.getInputStream(tomlEntry);
               BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
              line = line.trim();
              if (line.startsWith("modId=") || line.startsWith("modId =")) {
                String[] p = line.split("=", 2);
                slug = p[1].trim().replace("\"", "").replace("'", "");
              } else if (line.startsWith("version=") || line.startsWith("version =")) {
                String[] p = line.split("=", 2);
                version = p[1].trim().replace("\"", "").replace("'", "");
              }
            }
          }
        }
      }
    } catch (Exception ignored) {
    }

    Matcher m = FILENAME_PATTERN.matcher(filename);
    if (m.matches()) {
      String fnSlug = m.group(1).trim();
      String fnVer = m.group(2).trim();
      if (slug == null || slug.isEmpty()) {
        slug = fnSlug;
      }
      if (version == null || version.isEmpty() || "${file.jarVersion}".equals(version)) {
        version = fnVer;
      }
    }

    if (slug == null || slug.isEmpty()) {
      slug = filename.substring(0, filename.length() - 4);
    }
    if (version == null || version.isEmpty() || "${file.jarVersion}".equals(version)) {
      version = "0.0.0";
    }

    slug = slug.toLowerCase().replaceAll("[^a-z0-9_-]", "");
    return new ModFileInfo(file, slug, version);
  }

  public static int compareVersions(String v1, String v2) {
    if (v1 == null && v2 == null) return 0;
    if (v1 == null) return -1;
    if (v2 == null) return 1;

    String s1 = v1.trim();
    String s2 = v2.trim();
    if (s1.equalsIgnoreCase(s2)) return 0;

    String clean1 = s1.replaceAll("^[vV]", "").trim();
    String clean2 = s2.replaceAll("^[vV]", "").trim();

    String base1 = clean1.contains("+") ? clean1.substring(0, clean1.indexOf('+')) : clean1;
    String base2 = clean2.contains("+") ? clean2.substring(0, clean2.indexOf('+')) : clean2;

    if (base1.equalsIgnoreCase(base2)) {
      if (clean1.equalsIgnoreCase(clean2) || clean1.startsWith(clean2 + "+") || clean2.startsWith(clean1 + "+")) {
        return 0;
      }
    }

    String[] parts1 = base1.split("[.-]");
    String[] parts2 = base2.split("[.-]");

    int maxLen = Math.max(parts1.length, parts2.length);
    for (int i = 0; i < maxLen; i++) {
      String p1 = i < parts1.length ? parts1[i] : "0";
      String p2 = i < parts2.length ? parts2[i] : "0";

      boolean isNum1 = p1.matches("\\d+");
      boolean isNum2 = p2.matches("\\d+");

      if (isNum1 && isNum2) {
        try {
          long n1 = Long.parseLong(p1);
          long n2 = Long.parseLong(p2);
          if (n1 != n2) {
            return Long.compare(n1, n2);
          }
        } catch (NumberFormatException e) {
          int cmp = p1.compareToIgnoreCase(p2);
          if (cmp != 0) return cmp;
        }
      } else {
        int cmp = p1.compareToIgnoreCase(p2);
        if (cmp != 0) return cmp;
      }
    }

    return clean1.compareToIgnoreCase(clean2);
  }

  public static void cleanOldModVersions(String slug, String keepFilename) {
    try {
      File modsDir = new File("mods");
      if (!modsDir.exists() || !modsDir.isDirectory()) return;
      File[] files = modsDir.listFiles((dir, name) -> name.endsWith(".jar"));
      if (files == null) return;
      String prefix = slug.toLowerCase() + "-";
      for (File f : files) {
        String fname = f.getName().toLowerCase();
        if ((fname.startsWith(prefix) || fname.equals(slug.toLowerCase() + ".jar"))
            && !f.getName().equalsIgnoreCase(keepFilename)) {
          f.delete();
        }
      }
    } catch (Exception ignored) {
    }
  }

  public static UpdateResult checkAndUpgradeMod(ModFileInfo mod, String gameVersion, String loader,
                                                String email, String curseAPI,
                                                Consumer<String> statusCallback) {
    if (mod == null || mod.slug.isEmpty()) {
      return new UpdateResult(mod, "unknown", "", UpdateStatus.SKIPPED, "Invalid mod file metadata.");
    }

    String safeSlug = mod.slug.toLowerCase().replaceAll("[^a-z0-9_-]", "");
    if (statusCallback != null) {
      statusCallback.accept("Checking Modrinth for " + safeSlug + " (MC " + gameVersion + ", " + loader + ")...");
    }

    try {
      String encLoader = URLEncoder.encode("[\"" + loader + "\"]", StandardCharsets.UTF_8);
      String encVersion = URLEncoder.encode("[\"" + gameVersion + "\"]", StandardCharsets.UTF_8);
      String url = "https://api.modrinth.com/v2/project/" + safeSlug + "/version?loaders=" + encLoader
          + "&game_versions=" + encVersion;

      HttpRequest req = HttpRequest.newBuilder()
          .uri(URI.create(url))
          .header("User-Agent", "DeployedReject/MurCes/1.6.0 (" + (email != null ? email : "user@murces.local") + ")")
          .GET()
          .build();

      HttpResponse<String> resp = NetworkUtils.attemptS(req);
      if (resp != null && resp.statusCode() == 200) {
        JsonArray arr = JsonParser.parseString(resp.body()).getAsJsonArray();
        if (!arr.isEmpty()) {
          JsonObject latestObj = arr.get(0).getAsJsonObject();
          String remoteVer = latestObj.has("version_number") ? latestObj.get("version_number").getAsString() : "";
          if (remoteVer.isEmpty() && latestObj.has("name")) {
            remoteVer = latestObj.get("name").getAsString();
          }

          String downloadUrl = "";
          String originalFilename = "";
          if (latestObj.has("files") && latestObj.getAsJsonArray("files").size() > 0) {
            JsonObject f = latestObj.getAsJsonArray("files").get(0).getAsJsonObject();
            downloadUrl = f.get("url").getAsString();
            originalFilename = f.get("filename").getAsString();
          }

          if (downloadUrl.isEmpty()) {
            return new UpdateResult(mod, remoteVer, "", UpdateStatus.ERROR, "No download URL available in metadata.");
          }

          int cmp = compareVersions(remoteVer, mod.version);
          String cleanVer = remoteVer.replaceAll("[^a-zA-Z0-9_.+-]", "");
          String targetFilename = safeSlug + "-" + cleanVer + ".jar";

          if (cmp > 0) {
            if (statusCallback != null) {
              statusCallback.accept("Upgrading " + safeSlug + " from " + mod.version + " to " + remoteVer + "...");
            }

            File tmpFile = new File("mods", targetFilename + ".tmp");
            File finalFile = new File("mods", targetFilename);

            HttpRequest dlReq = HttpRequest.newBuilder()
                .uri(URI.create(downloadUrl))
                .header("User-Agent", "DeployedReject/MurCes/1.6.0 (" + email + ")")
                .GET()
                .build();
            HttpResponse<InputStream> dlResp = NetworkUtils.attemptI(dlReq);
            if (dlResp == null || dlResp.statusCode() != 200) {
              return new UpdateResult(mod, remoteVer, "", UpdateStatus.ERROR, "Failed to download remote jar.");
            }

            try (InputStream in = dlResp.body(); FileOutputStream out = new FileOutputStream(tmpFile)) {
              byte[] buf = new byte[8192];
              int n;
              while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
              }
              out.flush();
            }

            Files.move(tmpFile.toPath(), finalFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

            if (!mod.file.getName().equalsIgnoreCase(targetFilename)) {
              try {
                mod.file.delete();
              } catch (Exception ignored) {
              }
            }
            cleanOldModVersions(safeSlug, targetFilename);

            return new UpdateResult(mod, remoteVer, targetFilename, UpdateStatus.UPGRADED,
                "Successfully upgraded from " + mod.version + " to " + remoteVer);
          } else {
            String normVer = mod.version.replaceAll("[^a-zA-Z0-9_.+-]", "");
            String normFilename = safeSlug + "-" + normVer + ".jar";
            if (!mod.file.getName().equalsIgnoreCase(normFilename)) {
              File normFile = new File("mods", normFilename);
              if (!normFile.exists()) {
                try {
                  Files.move(mod.file.toPath(), normFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                } catch (Exception ignored) {
                }
              }
            }
            return new UpdateResult(mod, mod.version, normFilename, UpdateStatus.ALREADY_LATEST,
                "Already up to date (" + mod.version + ")");
          }
        }
      }

      if (curseAPI != null && !curseAPI.trim().isEmpty()) {
        if (statusCallback != null) {
          statusCallback.accept("Checking CurseForge for " + safeSlug + "...");
        }
        int loaderType = 4;
        if ("forge".equalsIgnoreCase(loader)) loaderType = 1;
        else if ("cauldron".equalsIgnoreCase(loader)) loaderType = 2;
        else if ("liteLoader".equalsIgnoreCase(loader)) loaderType = 3;
        else if ("quilt".equalsIgnoreCase(loader)) loaderType = 5;
        else if ("neoForge".equalsIgnoreCase(loader)) loaderType = 6;

        String cfSearch = "https://api.curseforge.com/v1/mods/search?gameId=432&gameVersion=" + gameVersion
            + "&modLoaderType=" + loaderType + "&searchFilter=" + URLEncoder.encode(safeSlug, StandardCharsets.UTF_8)
            + "&pageSize=5";

        HttpRequest cfReq = HttpRequest.newBuilder()
            .uri(URI.create(cfSearch))
            .header("x-api-key", curseAPI)
            .header("Accept", "application/json")
            .GET()
            .build();
        HttpResponse<String> cfResp = NetworkUtils.attemptS(cfReq);
        if (cfResp != null && cfResp.statusCode() == 200) {
          JsonObject cfRoot = JsonParser.parseString(cfResp.body()).getAsJsonObject();
          if (cfRoot.has("data") && cfRoot.getAsJsonArray("data").size() > 0) {
            JsonObject cfMod = cfRoot.getAsJsonArray("data").get(0).getAsJsonObject();
            String modId = cfMod.get("id").getAsString();

            String cfFilesUrl = "https://api.curseforge.com/v1/mods/" + modId + "/files?gameVersion=" + gameVersion
                + "&modLoaderType=" + loaderType;
            HttpRequest filesReq = HttpRequest.newBuilder()
                .uri(URI.create(cfFilesUrl))
                .header("x-api-key", curseAPI)
                .header("Accept", "application/json")
                .GET()
                .build();
            HttpResponse<String> filesResp = NetworkUtils.attemptS(filesReq);
            if (filesResp != null && filesResp.statusCode() == 200) {
              JsonObject fRoot = JsonParser.parseString(filesResp.body()).getAsJsonObject();
              if (fRoot.has("data") && fRoot.getAsJsonArray("data").size() > 0) {
                JsonObject latestFile = fRoot.getAsJsonArray("data").get(0).getAsJsonObject();
                String cfVer = latestFile.has("displayName") ? latestFile.get("displayName").getAsString()
                    : latestFile.get("fileName").getAsString();
                if (cfVer.toLowerCase().endsWith(".jar")) {
                  cfVer = cfVer.substring(0, cfVer.length() - 4);
                }
                String dUrl = latestFile.has("downloadUrl") && !latestFile.get("downloadUrl").isJsonNull()
                    ? latestFile.get("downloadUrl").getAsString() : "";

                if (!dUrl.isEmpty()) {
                  int cmp = compareVersions(cfVer, mod.version);
                  String cleanVer = cfVer.replaceAll("[^a-zA-Z0-9_.+-]", "");
                  String targetFilename = safeSlug + "-" + cleanVer + ".jar";

                  if (cmp > 0) {
                    File tmpFile = new File("mods", targetFilename + ".tmp");
                    File finalFile = new File("mods", targetFilename);
                    HttpRequest cfDl = HttpRequest.newBuilder()
                        .uri(URI.create(dUrl))
                        .header("x-api-key", curseAPI)
                        .GET()
                        .build();
                    HttpResponse<InputStream> cfDlResp = NetworkUtils.attemptI(cfDl);
                    if (cfDlResp != null && cfDlResp.statusCode() == 200) {
                      try (InputStream in = cfDlResp.body(); FileOutputStream out = new FileOutputStream(tmpFile)) {
                        byte[] buf = new byte[8192];
                        int n;
                        while ((n = in.read(buf)) != -1) {
                          out.write(buf, 0, n);
                        }
                        out.flush();
                      }
                      Files.move(tmpFile.toPath(), finalFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                      if (!mod.file.getName().equalsIgnoreCase(targetFilename)) {
                        try {
                          mod.file.delete();
                        } catch (Exception ignored) {
                        }
                      }
                      cleanOldModVersions(safeSlug, targetFilename);
                      return new UpdateResult(mod, cfVer, targetFilename, UpdateStatus.UPGRADED,
                          "Successfully upgraded via CurseForge to " + cfVer);
                    }
                  } else {
                    return new UpdateResult(mod, mod.version, mod.file.getName(), UpdateStatus.ALREADY_LATEST,
                        "Already up to date on CurseForge (" + mod.version + ")");
                  }
                }
              }
            }
          }
        }
      }

      return new UpdateResult(mod, mod.version, mod.file.getName(), UpdateStatus.NOT_FOUND,
          "No compatible release found for " + gameVersion + " (" + loader + ")");

    } catch (Exception e) {
      return new UpdateResult(mod, mod.version, mod.file.getName(), UpdateStatus.ERROR, "Error checking update: " + e.getMessage());
    }
  }

  public static UpdateSummary updateAllMods(String gameVersion, String loader, String email, String curseAPI,
                                           Consumer<String> logCallback,
                                           Consumer<Double> progressCallback) {
    UpdateSummary summary = new UpdateSummary();
    File modsDir = new File("mods");
    if (!modsDir.exists() || !modsDir.isDirectory()) {
      if (logCallback != null) logCallback.accept("No mods directory found.");
      return summary;
    }

    File[] files = modsDir.listFiles((dir, name) -> name.endsWith(".jar") && !name.endsWith(".tmp"));
    if (files == null || files.length == 0) {
      if (logCallback != null) logCallback.accept("No mod jars found in ./mods folder.");
      return summary;
    }

    Arrays.sort(files, Comparator.comparing(File::getName));
    summary.total = files.length;

    if (logCallback != null) {
      logCallback.accept("Beginning mod updates for " + files.length + " mod(s) (Minecraft " + gameVersion + ", " + loader + ")...");
    }

    for (int i = 0; i < files.length; i++) {
      File f = files[i];
      ModFileInfo info = parseModFile(f);
      if (info == null) {
        summary.notFound++;
        continue;
      }

      double currentPct = (i * 100.0) / files.length;
      if (progressCallback != null) {
        progressCallback.accept(currentPct);
      }

      if (logCallback != null) {
        logCallback.accept(String.format("[%d/%d] Checking %s (v%s)...", i + 1, files.length, info.slug, info.version));
      }

      UpdateResult res = checkAndUpgradeMod(info, gameVersion, loader, email, curseAPI, logCallback);
      summary.results.add(res);

      switch (res.status) {
        case UPGRADED:
          summary.upgraded++;
          if (logCallback != null) {
            logCallback.accept(String.format("✔ [UPGRADED] %s -> %s (%s)", info.slug, res.newVersion, res.newFilename));
          }
          break;
        case ALREADY_LATEST:
          summary.alreadyUpToDate++;
          if (logCallback != null) {
            logCallback.accept(String.format("✔ [UP TO DATE] %s is already at version %s", info.slug, info.version));
          }
          break;
        case NOT_FOUND:
          summary.notFound++;
          if (logCallback != null) {
            logCallback.accept(String.format("• [SKIPPED] %s: %s", info.slug, res.message));
          }
          break;
        case ERROR:
          summary.errors++;
          if (logCallback != null) {
            logCallback.accept(String.format("✖ [ERROR] %s: %s", info.slug, res.message));
          }
          break;
        default:
          break;
      }
    }

    if (progressCallback != null) {
      progressCallback.accept(100.0);
    }
    if (logCallback != null) {
      logCallback.accept("Mod update finished! " + summary.formattedSummary());
    }

    return summary;
  }
}
