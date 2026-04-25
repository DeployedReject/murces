package org.codeberg.DeployedReject;

import com.google.gson.JsonParser;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import java.io.InputStream;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.net.URLEncoder;
import java.net.URI;
import java.nio.charset.StandardCharsets;

public class Modrinth {
  String type;
  String query = "mods";
  String version;
  String loader;
  String email;

  public void modrinthHandler() {

    if (type.equals("search"))
      search(true);
    else if (type.equals("download")) {
      download();
    } else if (type.equals("home")) {
      search(false);

    }

  }

  private void download() {
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
    try {
      HttpResponse<String> result = Main.device.send(downloading, BodyHandlers.ofString());

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
            HttpResponse<InputStream> downloadRequest = Main.device.send(downloading,
                HttpResponse.BodyHandlers.ofInputStream());

            if (downloadRequest.statusCode() == 200) {

              long filesize = downloadRequest.headers().firstValueAsLong("content-length").orElse(-1L);
              Progress.prog(downloadRequest.body(), "mods/"
                  + downloadLink.get("files").getAsJsonArray().get(0).getAsJsonObject().get("filename").getAsString(),
                  filesize);
            } else {

              ErrorHelper.errorJson("Website returned status code" + result.statusCode());
            }
          } catch (Exception e) {
            ErrorHelper.errorJson(e.toString());
          }

        } else {
          ErrorHelper.errorJson("No valid Link");
        }

      } else {

        ErrorHelper.errorJson("Website returned status code" + result.statusCode());
      }
    } catch (Exception e) {
      ErrorHelper.errorJson(e.toString());
    }

  }

  private void search(boolean mode) {

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
    url = url + "?query=" + query + "&facets=" + facets + "&limit=10";

    HttpRequest searching = HttpRequest
        .newBuilder()
        .uri(URI.create(url))
        .header("User-Agent", "DeployedReject/MurCes/0.1 (" + email + ")")
        .GET()
        .build();

    try {
      HttpResponse<String> result = Main.device.send(searching, BodyHandlers.ofString());

      if (result.statusCode() == 200) {

        JsonObject response = JsonParser.parseString(result.body()).getAsJsonObject();
        Communicator.printer(sTranslator(response));
      } else {

        ErrorHelper.errorJson("Website returned status code" + result.statusCode());
      }
    } catch (Exception e) {
      ErrorHelper.errorJson(e.toString());
    }

  }

  private JsonObject sTranslator(JsonObject x) {

    JsonObject response = new JsonObject();
    response.addProperty("status", 0);
    response.addProperty("type", "query");

    JsonArray mods = new JsonArray();

    for (int i = 0; i < x.get("hits").getAsJsonArray().size(); i++) {
      JsonArray mod = new JsonArray();
      mod.add(x.get("hits").getAsJsonArray().get(i).getAsJsonObject().get("slug").getAsString());
      mod.add(x.get("hits").getAsJsonArray().get(i).getAsJsonObject().get("title").getAsString());
      mods.add(mod);

    }

    response.add("mods", mods);

    return response;

  }
}
