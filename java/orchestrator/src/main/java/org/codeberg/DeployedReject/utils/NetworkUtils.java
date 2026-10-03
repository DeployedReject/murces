package org.codeberg.DeployedReject.utils;

import java.io.InputStream;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import com.google.gson.JsonObject;
import org.codeberg.DeployedReject.Main;
import java.io.FileOutputStream;

public class NetworkUtils {

  public static void prog(InputStream x, String filename, long filesize) {

    String tempFilename = filename + ".tmp";
    java.io.File tempFile = new java.io.File(tempFilename);
    boolean success = false;

    try (FileOutputStream file = new FileOutputStream(tempFile)) {

      try {
        byte[] buffer = new byte[8192];
        int readSize = 0;
        int bytesRead;
        JsonObject response = new JsonObject();
        response.addProperty("status", 2);
        response.addProperty("type", "download");
        response.addProperty("id", filename);
        response.addProperty("progress", 0);
        response.addProperty("read", 0L);
        response.addProperty("total", filesize);

        Communicator.printer(response);

        response.addProperty("status", 0);

        long lastProgressTime = 0;
        while ((bytesRead = x.read(buffer)) != -1) {
          if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException("Download cancelled");
          }
          file.write(buffer, 0, bytesRead);
          readSize += bytesRead;
          long now = System.currentTimeMillis();
          if (now - lastProgressTime >= 500 || (filesize > 0 && readSize >= filesize)) {
            lastProgressTime = now;
            double pct = filesize > 0 ? Math.min(100.0, (readSize * 100.0) / filesize) : -1.0;
            double rounded = pct >= 0 ? (Math.round(pct * 100.0) / 100.0) : -1.0;
            response.addProperty("progress", rounded);
            response.addProperty("read", (long) readSize);
            response.addProperty("total", filesize);
            Communicator.printer(response);
          }
        }

        file.flush();
        success = true;

        java.nio.file.Path source = tempFile.toPath();
        java.nio.file.Path target = java.nio.file.Paths.get(filename);
        try {
          java.nio.file.Files.move(source, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception moveEx) {
          java.nio.file.Files.move(source, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }

        JsonObject responseEnd = new JsonObject();
        responseEnd.addProperty("status", 3);
        responseEnd.addProperty("type", "download");
        responseEnd.addProperty("id", filename);
        responseEnd.addProperty("progress", 100.0);
        responseEnd.addProperty("read", (long) readSize);
        responseEnd.addProperty("total", filesize > 0 ? filesize : (long) readSize);
        Communicator.printer(responseEnd);
      } catch (Exception e) {
        ErrorHelper.errorJson("Failed while downloading '" + filename + "': " + (e.getMessage() != null ? e.getMessage() : e.toString()));
      }

    } catch (Exception e) {
      ErrorHelper.errorJson("I/O error saving '" + filename + "': " + (e.getMessage() != null ? e.getMessage() : e.toString()));
    } finally {
      if (!success && tempFile.exists()) {
        try {
          tempFile.delete();
        } catch (Exception ignored) {}
      }
      try {
        x.close();
      } catch (Exception ignored) {
      }
    }

  }

  public static HttpResponse<String> attemptS(HttpRequest x) {

    HttpResponse<String> y;
    try {
      y = Main.device.send(x, BodyHandlers.ofString());

    } catch (Exception e) {
      ErrorHelper.errorJson("Network request failed (" + x.uri().getHost() + "): " + (e.getMessage() != null ? e.getMessage() : e.toString()));
      return null;
    }

    if (y.statusCode() != 200) {
      ErrorHelper.errorJson("Remote server (" + x.uri().getHost() + ") returned HTTP status " + y.statusCode());
      return null;
    }

    return y;

  }

  public static HttpResponse<InputStream> attemptI(HttpRequest x) {
    HttpResponse<InputStream> y;

    try {
      y = Main.device.send(x, BodyHandlers.ofInputStream());
    } catch (Exception e) {
      ErrorHelper.errorJson("Download connection failed (" + x.uri().getHost() + "): " + (e.getMessage() != null ? e.getMessage() : e.toString()));
      return null;
    }

    if (y.statusCode() != 200) {
      ErrorHelper.errorJson("Download request to " + x.uri().getHost() + " failed with HTTP status " + y.statusCode());
      return null;
    }

    return y;
  }

}
