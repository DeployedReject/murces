package org.codeberg.DeployedReject;

import java.io.InputStream;
import com.google.gson.JsonObject;
import java.io.FileOutputStream;

public class Progress {

  public static void prog(InputStream x, String filename, long filesize) {

    try (FileOutputStream file = new FileOutputStream(filename)) {

      try {
        byte[] buffer = new byte[4096];
        int readSize = 0;
        int bytesRead;
        JsonObject response = new JsonObject();
        response.addProperty("status", 2);
        response.addProperty("type", "download");
        response.addProperty("progress", 0);

        Communicator.printer(response);

        response.addProperty("status", 0);

        while ((bytesRead = x.read(buffer)) != -1) {
          file.write(buffer, 0, bytesRead);
          readSize += bytesRead;
          response.addProperty("progress", (readSize * 100.0) / filesize);
          Communicator.printer(response);
        }

        response.addProperty("status", 3);
        Communicator.printer(response);
      } catch (Exception e) {
        ErrorHelper.errorJson(e.toString());
      }

    } catch (Exception e) {
      ErrorHelper.errorJson(e.toString());
    }

  }
}
