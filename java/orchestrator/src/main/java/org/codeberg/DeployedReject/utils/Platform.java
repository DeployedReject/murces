package org.codeberg.DeployedReject.utils;

import java.io.File;

public class Platform {

  private static final String OS = System.getProperty("os.name", "").toLowerCase();
  private static final boolean IS_WINDOWS = OS.contains("win");
  private static final boolean IS_MAC = OS.contains("mac") || OS.contains("darwin");
  private static final boolean IS_LINUX = OS.contains("linux") || OS.contains("unix");

  public static boolean isWindows() {
    return IS_WINDOWS;
  }

  public static boolean isMac() {
    return IS_MAC;
  }

  public static boolean isLinux() {
    return IS_LINUX;
  }

  public static String getMultiplexer() {
    return IS_WINDOWS ? "psmux" : "tmux";
  }

  public static boolean isCommandAvailable(String cmd) {
    if (cmd == null || cmd.trim().isEmpty()) {
      return false;
    }
    String cleanCmd = cmd.trim();

    try {
      String lookupTool = IS_WINDOWS ? "where" : "which";
      Process p = new ProcessBuilder(lookupTool, cleanCmd).start();
      if (p.waitFor() == 0) {
        return true;
      }
    } catch (Exception ignored) {
    }

    String pathEnv = System.getenv("PATH");
    if (pathEnv != null) {
      String[] dirs = pathEnv.split(File.pathSeparator);
      for (String dir : dirs) {
        File file = new File(dir, cleanCmd);
        if (file.exists() && (IS_WINDOWS || file.canExecute())) {
          return true;
        }
        if (IS_WINDOWS) {
          for (String ext : new String[] { ".exe", ".cmd", ".bat", ".ps1" }) {
            File fExt = new File(dir, cleanCmd + ext);
            if (fExt.exists()) {
              return true;
            }
          }
        }
      }
    }
    return false;
  }
}
