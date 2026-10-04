package org.codeberg.DeployedReject.device;

import org.codeberg.DeployedReject.utils.ProcessResult;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;

public class BackupHandler {

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

  public static ProcessResult runBackup(String sourceFolder, String targetFolder, int retentionLimit,
      boolean cloudSync, String cloudRemote) {
    if (sourceFolder == null || sourceFolder.trim().isEmpty()) {
      sourceFolder = "world";
    } else {
      sourceFolder = sourceFolder.trim();
      while ((sourceFolder.endsWith("/") || sourceFolder.endsWith("\\")) && sourceFolder.length() > 1) {
        sourceFolder = sourceFolder.substring(0, sourceFolder.length() - 1);
      }
    }
    if (targetFolder == null || targetFolder.trim().isEmpty()) {
      targetFolder = "backup";
    }
    if (retentionLimit <= 0) {
      retentionLimit = 3;
    }
    if (cloudRemote == null || cloudRemote.trim().isEmpty()) {
      cloudRemote = "minecraftdrive";
    }

    StringBuilder logOutput = new StringBuilder();
    File sourceDir = new File(sourceFolder);
    if (!sourceDir.exists() || !sourceDir.isDirectory()) {
      String err = "Error: Could not find a folder named '" + sourceFolder + "'.\n";
      appendToLog("backup.log", err);
      writeStatus("backup.status.log", err);
      return new ProcessResult(1, err.trim());
    }

    String startMsg = "Success: The folder '" + sourceFolder + "' exists in the current directory.\n";
    logOutput.append(startMsg);
    appendToLog("backup.log", startMsg);
    writeStatus("backup.status.log", startMsg);

    boolean serverWasRunning = ServerHandler.isServerRunning();
    if (serverWasRunning) {
      ServerHandler.sendConsoleCommand("save-all");
      ServerHandler.sendConsoleCommand("save-off");
      try {
        Thread.sleep(1500);
      } catch (InterruptedException ignored) {
      }
      logOutput.append("Save Done\n");
      appendToLog("backup.log", "Save Done\n");
      writeStatus("backup.status.log", "Save Done\n");
    }

    try {
      File targetDir = new File(targetFolder);
      if (!targetDir.exists()) {
        targetDir.mkdirs();
      }

      String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
      String archiveName = timestamp + ".tar";
      File archiveFile = new File(targetDir, archiveName);

      List<String> tarArgs = new ArrayList<>(Arrays.asList("tar", "-cf", archiveFile.getPath(), sourceFolder));

      File netherDir = new File(sourceFolder + "_nether");
      if (netherDir.exists() && netherDir.isDirectory()) {
        tarArgs.add(netherDir.getPath());
        String netherMsg = "Included Bukkit/Paper Nether folder: '" + netherDir.getPath() + "'\n";
        logOutput.append(netherMsg);
        appendToLog("backup.log", netherMsg);
        writeStatus("backup.status.log", netherMsg);
      }

      File endDir = new File(sourceFolder + "_the_end");
      if (endDir.exists() && endDir.isDirectory()) {
        tarArgs.add(endDir.getPath());
        String endMsg = "Included Bukkit/Paper The End folder: '" + endDir.getPath() + "'\n";
        logOutput.append(endMsg);
        appendToLog("backup.log", endMsg);
        writeStatus("backup.status.log", endMsg);
      }

      ProcessBuilder pb = new ProcessBuilder(tarArgs);
      pb.redirectErrorStream(true);
      Process p = pb.start();
      int tarCode = p.waitFor();
      if (tarCode != 0) {
        String err = "Tar bundling failed with code " + tarCode + "\n";
        logOutput.append(err);
        appendToLog("backup.log", err);
        writeStatus("backup.status.log", err);
        return new ProcessResult(tarCode, logOutput.toString().trim());
      }

      logOutput.append("Bundling Done\n");
      appendToLog("backup.log", "Bundling Done\n");
      writeStatus("backup.status.log", "Bundling Done\n");

      // Retention cleanup
      File[] archives = targetDir.listFiles((dir, name) -> {
        String l = name.toLowerCase();
        return l.endsWith(".tar") || l.endsWith(".tar.gz") || l.endsWith(".tgz");
      });
      if (archives != null && archives.length > retentionLimit) {
        Arrays.sort(archives, Comparator.comparingLong(File::lastModified));
        int toDelete = archives.length - retentionLimit;
        for (int i = 0; i < toDelete; i++) {
          File oldest = archives[i];
          String delMsg = "Deleted " + oldest.getPath() + " (Total: " + archives.length + ")\n";
          oldest.delete();
          logOutput.append(delMsg);
          appendToLog("backup.log", delMsg);
          writeStatus("backup.status.log", delMsg);
        }
      }

      // Cloud sync via rclone
      if (cloudSync) {
        logOutput.append("Upload Started\n");
        appendToLog("backup.log", "Upload Started\n");
        writeStatus("backup.status.log", "Upload Started\n");

        if (ServerHandler.isCommandAvailable("rclone")) {
          ProcessBuilder rclonePb = new ProcessBuilder("rclone", "sync", targetFolder,
              cloudRemote + ":" + targetFolder);
          rclonePb.redirectErrorStream(true);
          Process rcloneProc = rclonePb.start();
          int rcloneCode = rcloneProc.waitFor();
          if (rcloneCode == 0) {
            logOutput.append("Upload Done\n");
            appendToLog("backup.log", "Upload Done\n");
            writeStatus("backup.status.log", "Upload Done\n");
          } else {
            String err = "rclone sync failed with exit code " + rcloneCode + "\n";
            logOutput.append(err);
            appendToLog("backup.log", err);
            writeStatus("backup.status.log", err);
          }
        } else {
          String warn = "[WARN] rclone command not found in PATH. Cloud upload skipped.\n";
          logOutput.append(warn);
          appendToLog("backup.log", warn);
          writeStatus("backup.status.log", warn);
        }
      }

      return new ProcessResult(0, logOutput.toString().trim());

    } catch (Exception e) {
      String err = "Backup exception: " + e.getMessage() + "\n";
      logOutput.append(err);
      appendToLog("backup.log", err);
      writeStatus("backup.status.log", err);
      return new ProcessResult(1, logOutput.toString().trim());
    } finally {
      if (serverWasRunning) {
        ServerHandler.sendConsoleCommand("save-on");
      }
    }
  }

  public static List<BackupInfo> listBackups(String targetFolder) {
    List<BackupInfo> list = new ArrayList<>();
    List<File> dirs = new ArrayList<>();
    if (targetFolder != null && !targetFolder.trim().isEmpty()) {
      File customDir = new File(targetFolder.trim());
      if (customDir.exists() && customDir.isDirectory()) {
        dirs.add(customDir);
      }
    }
    File d1 = new File("backup");
    if (d1.exists() && d1.isDirectory() && !dirs.contains(d1))
      dirs.add(d1);
    File d2 = new File("backups");
    if (d2.exists() && d2.isDirectory() && !dirs.contains(d2))
      dirs.add(d2);
    if (dirs.isEmpty()) {
      dirs.add(new File("."));
    }

    Set<String> seen = new HashSet<>();
    List<File> allFiles = new ArrayList<>();
    for (File dir : dirs) {
      File[] files = dir.listFiles((d, name) -> {
        String lower = name.toLowerCase();
        return lower.endsWith(".tar") || lower.endsWith(".tar.gz") || lower.endsWith(".tgz")
            || lower.endsWith(".zip");
      });
      if (files != null) {
        for (File f : files) {
          if (f.isFile() && seen.add(f.getName())) {
            allFiles.add(f);
          }
        }
      }
    }

    allFiles.sort((a, b) -> Long.compare(b.lastModified(), a.lastModified()));
    for (File f : allFiles) {
      list.add(new BackupInfo(f.getName(), f.length(), f.lastModified()));
    }
    return list;
  }

  public static boolean deleteBackup(String targetFolder, String filename) {
    if (filename == null || filename.trim().isEmpty() || filename.contains("..") || filename.contains("/")
        || filename.contains("\\")) {
      return false;
    }
    if (targetFolder != null && !targetFolder.trim().isEmpty()) {
      File customFile = new File(targetFolder.trim(), filename);
      if (customFile.exists() && customFile.isFile()) {
        return customFile.delete();
      }
    }
    File f1 = new File("backup", filename);
    if (f1.exists() && f1.isFile()) {
      return f1.delete();
    }
    File f2 = new File("backups", filename);
    if (f2.exists() && f2.isFile()) {
      return f2.delete();
    }
    File f3 = new File(".", filename);
    if (f3.exists() && f3.isFile()) {
      return f3.delete();
    }
    return false;
  }

  private static void appendToLog(String filename, String content) {
    try (FileWriter fw = new FileWriter(filename, StandardCharsets.UTF_8, true)) {
      fw.write(content);
    } catch (Exception ignored) {
    }
  }

  private static void writeStatus(String filename, String content) {
    try (FileWriter fw = new FileWriter(filename, StandardCharsets.UTF_8, false)) {
      fw.write(content);
    } catch (Exception ignored) {
    }
  }
}
