package org.codeberg.DeployedReject;

public class Shell {
  public static Process execute(String[] command) {
    ProcessBuilder executor = new ProcessBuilder();

    executor.command(command);

    try {

      Process running = executor.start();

      return running;

    } catch (Exception e) {
      ErrorHelper.errorJson(e.toString());
      return null;
    }

  }
}
