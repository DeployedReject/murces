package org.codeberg.DeployedReject.device;

import com.google.gson.JsonObject;
import org.codeberg.DeployedReject.Main;
import org.codeberg.DeployedReject.utils.Communicator;
import org.codeberg.DeployedReject.utils.ErrorHelper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.function.Consumer;

public class JdkManager {

  public static int getRequiredJdkVersion(String mcVersion) {
    if (mcVersion == null || mcVersion.trim().isEmpty()) {
      return 21;
    }
    String v = mcVersion.trim();
    if (v.startsWith("1.")) {
      String[] parts = v.split("\\.");
      try {
        int minor = Integer.parseInt(parts[1]);
        int patch = parts.length > 2 ? Integer.parseInt(parts[2].replaceAll("[^0-9]", "")) : 0;
        if (minor >= 21) {
          return 21;
        } else if (minor == 20) {
          return patch >= 5 ? 21 : 17;
        } else if (minor >= 17) {
          return 17;
        } else {
          return 8;
        }
      } catch (Exception e) {
        return 21;
      }
    }
    return 21;
  }

  public static File getJdkHome(int majorVersion) {
    return new File("jdks", "jdk-" + majorVersion);
  }

  public static File getJavaExecutable(int majorVersion) {
    return new File(getJdkHome(majorVersion), "bin/java");
  }

  public static boolean isJdkInstalled(int majorVersion) {
    File javaBin = getJavaExecutable(majorVersion);
    return javaBin.exists() && javaBin.canExecute();
  }

  public static String getJavaCommand(String mcVersion, boolean usePortable) {
    int req = getRequiredJdkVersion(mcVersion);
    File portableBin = getJavaExecutable(req);
    if (usePortable) {
      if (portableBin.exists() && portableBin.canExecute()) {
        return portableBin.getAbsolutePath();
      }
    }
    if (ServerHandler.isCommandAvailable("java")) {
      return "java";
    }
    if (portableBin.exists() && portableBin.canExecute()) {
      return portableBin.getAbsolutePath();
    }
    return "java";
  }

  public static String getJdkBinDir(int majorVersion) {
    return new File(getJdkHome(majorVersion), "bin").getAbsolutePath();
  }

  public static synchronized boolean downloadAndExtractJdk(int majorVersion,
                                                           Consumer<Double> progressCallback,
                                                           Consumer<String> statusCallback) {
    if (isJdkInstalled(majorVersion)) {
      if (statusCallback != null) {
        statusCallback.accept("Portable JDK " + majorVersion + " is already installed.");
      }
      return true;
    }

    String arch = System.getProperty("os.arch", "x86_64").toLowerCase();
    String adoptiumArch = (arch.contains("aarch64") || arch.contains("arm64")) ? "aarch64" : "x64";
    String url = "https://api.adoptium.net/v3/binary/latest/" + majorVersion
        + "/ga/linux/" + adoptiumArch + "/jdk/hotspot/normal/eclipse";

    File jdksDir = new File("jdks");
    if (!jdksDir.exists()) {
      jdksDir.mkdirs();
    }

    File tarTmp = new File(jdksDir, "jdk-" + majorVersion + ".tar.gz.tmp");
    File tarFinal = new File(jdksDir, "jdk-" + majorVersion + ".tar.gz");
    File targetDir = getJdkHome(majorVersion);

    if (statusCallback != null) {
      statusCallback.accept("Downloading Portable JDK " + majorVersion + " (" + adoptiumArch + ")...");
    }

    JsonObject startMsg = new JsonObject();
    startMsg.addProperty("status", 2);
    startMsg.addProperty("type", "jdk");
    startMsg.addProperty("id", "jdk-" + majorVersion);
    startMsg.addProperty("progress", 0.0);
    startMsg.addProperty("message", "Downloading Portable JDK " + majorVersion + " from Adoptium...");
    Communicator.printer(startMsg);

    try {
      HttpRequest req = HttpRequest.newBuilder()
          .uri(URI.create(url))
          .header("User-Agent", "DeployedReject/MurCes/1.2.0")
          .GET()
          .build();

      HttpResponse<InputStream> resp = Main.device.send(req, HttpResponse.BodyHandlers.ofInputStream());
      if (resp == null || resp.statusCode() != 200) {
        int code = resp != null ? resp.statusCode() : -1;
        ErrorHelper.errorJson("Failed to download JDK " + majorVersion + " from Adoptium (HTTP " + code + ")");
        return false;
      }

      long filesize = resp.headers().firstValueAsLong("content-length").orElse(-1L);
      byte[] buffer = new byte[16384];
      long totalRead = 0;
      long lastReport = 0;

      try (InputStream in = resp.body(); FileOutputStream out = new FileOutputStream(tarTmp)) {
        int n;
        while ((n = in.read(buffer)) != -1) {
          if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException("JDK download cancelled");
          }
          out.write(buffer, 0, n);
          totalRead += n;

          long now = System.currentTimeMillis();
          if (now - lastReport >= 400 || (filesize > 0 && totalRead == filesize)) {
            lastReport = now;
            double pct = filesize > 0 ? Math.min(100.0, (totalRead * 100.0) / filesize) : -1.0;
            if (pct >= 0) {
              if (progressCallback != null) {
                progressCallback.accept(pct);
              }
              JsonObject progMsg = new JsonObject();
              progMsg.addProperty("status", 2);
              progMsg.addProperty("type", "jdk");
              progMsg.addProperty("id", "jdk-" + majorVersion);
              progMsg.addProperty("progress", Math.round(pct * 10.0) / 10.0);
              Communicator.printer(progMsg);
            }
          }
        }
        out.flush();
      }

      Files.move(tarTmp.toPath(), tarFinal.toPath(), StandardCopyOption.REPLACE_EXISTING);

      if (statusCallback != null) {
        statusCallback.accept("Extracting Portable JDK " + majorVersion + "...");
      }

      if (!targetDir.exists()) {
        targetDir.mkdirs();
      }

      Process p = new ProcessBuilder("tar", "-xzf", tarFinal.getAbsolutePath(),
          "-C", targetDir.getAbsolutePath(), "--strip-components=1").start();
      int exit = p.waitFor();

      try {
        tarFinal.delete();
      } catch (Exception ignored) {
      }

      if (exit != 0) {
        ErrorHelper.errorJson("Failed to extract JDK " + majorVersion + " archive (tar exit code " + exit + ")");
        return false;
      }

      File javaBin = getJavaExecutable(majorVersion);
      if (javaBin.exists()) {
        javaBin.setExecutable(true, false);
      }

      File binDir = new File(targetDir, "bin");
      File[] allBins = binDir.listFiles();
      if (allBins != null) {
        for (File b : allBins) {
          b.setExecutable(true, false);
        }
      }

      JsonObject doneMsg = new JsonObject();
      doneMsg.addProperty("status", 3);
      doneMsg.addProperty("type", "jdk");
      doneMsg.addProperty("id", "jdk-" + majorVersion);
      doneMsg.addProperty("progress", 100.0);
      doneMsg.addProperty("message", "Portable JDK " + majorVersion + " installed successfully.");
      Communicator.printer(doneMsg);

      if (statusCallback != null) {
        statusCallback.accept("Portable JDK " + majorVersion + " installed successfully.");
      }

      return isJdkInstalled(majorVersion);

    } catch (Exception e) {
      if (tarTmp.exists()) {
        try {
          tarTmp.delete();
        } catch (Exception ignored) {
        }
      }
      ErrorHelper.errorJson("Failed to install portable JDK " + majorVersion + ": " + e.getMessage());
      return false;
    }
  }
}
