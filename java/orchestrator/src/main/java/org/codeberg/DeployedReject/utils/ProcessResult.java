package org.codeberg.DeployedReject.utils;

public class ProcessResult {
  public final int exitCode;
  public final String output;

  public ProcessResult(int exitCode, String output) {
    this.exitCode = exitCode;
    this.output = output != null ? output : "";
  }

  @Override
  public String toString() {
    return output;
  }
}
