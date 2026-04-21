package org.codeberg.DeployedReject;

import com.google.gson.JsonParser;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.net.URLEncoder;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.codeberg.DeployedReject.errorHelper;
import java.io.FileOutputStream;

public class modrinth {
  static String type;
  static String query = "mods";
  static String version;
  static String loader;
  static String email;

  public static void modrinthHandler() {

    if (type.equals("search"))
      search(true);
    else if (type.equals("download")) {
      download();
    } else if (type.equals("home")) {
      search(false);

    }

  }

  private static void download() {
    String url = "https://api.modrinth.com/v2/project/";
    loader = "[\"" + loader + "\"]";
    version = "[\"" + version + "\"]";

    loader = URLEncoder.encode(loader, StandardCharsets.UTF_8);
    version = URLEncoder.encode(version, StandardCharsets.UTF_8);

    url = url + query + "/version?loaders=" + loader + "&game_versions=" + version;

    HttpRequest downloading = HttpRequest
        .newBuilder()
        .uri(URI.create(url))
        .header("User-Agent", "DeployedReject/MurCes/0.1 (" + email + ")")
        .GET()
        .build();
    HttpClient device = HttpClient.newHttpClient();
    try {
      HttpResponse<String> result = device.send(downloading, BodyHandlers.ofString());

      if (result.statusCode() == 200) {

        JsonArray temp = JsonParser.parseString(result.body()).getAsJsonArray();

        if (!temp.isEmpty()) {
          JsonObject downloadLink = temp.get(0).getAsJsonObject();
          downloading = HttpRequest
              .newBuilder()
              .uri(URI
                  .create(downloadLink.get("files").getAsJsonArray().get(0).getAsJsonObject().get("url").getAsString()))
              .GET()
              .build();
          try {
            // Getting it as a InputStream to monitor download progress.
            HttpResponse<InputStream> downloadRequest = device.send(downloading,
                HttpResponse.BodyHandlers.ofInputStream());

            if (downloadRequest.statusCode() == 200) {
              FileOutputStream downloadedFile = new FileOutputStream(
                  downloadLink.get("files").getAsJsonArray().get(0).getAsJsonObject().get("filename").getAsString());
              byte[] buffer = new byte[4096];
              long filesize = downloadRequest.headers().firstValueAsLong("content-length").orElse(-1L);
              int readSize = 0;
              int bytesRead;
              InputStream writer = downloadRequest.body();
              JsonObject response = new JsonObject();
              response.addProperty("status", 0);
              response.addProperty("type", "download");
              response.addProperty("progress", 0);

              while ((bytesRead = writer.read(buffer)) != -1) {
                downloadedFile.write(buffer, 0, bytesRead);
                readSize += bytesRead;
                response.addProperty("progress", (readSize * 100.0) / filesize);
                System.out.println(response);
              }

              downloadedFile.close();
            } else {
              errorHelper.errorJson("Website returned status code" + result.statusCode());
            }
          } catch (Exception e) {
            errorHelper.errorJson(e.toString());
          }

        } else {
          errorHelper.errorJson("No valid Link");
        }

      } else {

        errorHelper.errorJson("Website returned status code" + result.statusCode());
      }
    } catch (Exception e) {
      errorHelper.errorJson(e.toString());
    }

  }

  private static void search(boolean mode) {

    String url = "https://api.modrinth.com/v2/search";
    String facets;
    if (mode) {

      facets = "[[\"project_type:mod\"],[ \"versions:" + version
          + "\"],[\"server_side:required\",\"server_side:optional\"],[\"categories:" + loader + "\"]]";
    } else {
      facets = "[[\"project_type:mod\"],[\"server_side:required\",\"server_side:optional\"]]";

    }
    query = URLEncoder.encode(query, StandardCharsets.UTF_8);
    facets = URLEncoder.encode(facets, StandardCharsets.UTF_8);
    url = url + "?query=" + query + "&facets=" + facets;

    HttpRequest searching = HttpRequest
        .newBuilder()
        .uri(URI.create(url))
        .header("User-Agent", "DeployedReject/MurCes/0.1 (" + email + ")")
        .GET()
        .build();

    HttpClient device = HttpClient.newHttpClient();

    try {
      HttpResponse<String> result = device.send(searching, BodyHandlers.ofString());

      if (result.statusCode() == 200) {

        JsonObject response = JsonParser.parseString(result.body()).getAsJsonObject();
        System.out.println(response);
      } else {

        errorHelper.errorJson("Website returned status code" + result.statusCode());
      }
    } catch (Exception e) {
      errorHelper.errorJson(e.toString());
    }

  }
}
