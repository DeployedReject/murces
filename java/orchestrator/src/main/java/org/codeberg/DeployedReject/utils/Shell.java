package org.codeberg.DeployedReject.utils;

public class Shell {
  public static Process execute(String[] command) {
    ProcessBuilder executor = new ProcessBuilder();

    executor.command(command);

    try {
      executor.redirectOutput(ProcessBuilder.Redirect.DISCARD);
      executor.redirectError(ProcessBuilder.Redirect.DISCARD);
      Process running = executor.start();

      return running;

    } catch (Exception e) {
      String cmdName = (command != null && command.length > 0) ? command[0] : "process";
      ErrorHelper.errorJson("Failed to execute command '" + cmdName + "': " + (e.getMessage() != null ? e.getMessage() : e.toString()));
      return null;
    }

  }
}
