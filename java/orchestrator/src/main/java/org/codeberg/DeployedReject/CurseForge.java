package org.codeberg.DeployedReject;

import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.http.HttpResponse;

public class CurseForge {

  public String type;
  public int id = 432;
  public String modName;
  public String version;
  public String loader;
  public String API;
  public String modId;

  HashMap<String, Integer> mLT = new HashMap<>(); // Mod Loader Translator

  String baseURL = "https://api.curseforge.com";

  public void curseForgeHandler() {
    mLT.put("forge", 1);
    mLT.put("fabric", 4);
    mLT.put("liteLoader", 3);
    mLT.put("cauldron", 2);
    mLT.put("quilt", 5);
    mLT.put("neoForge", 6);
    if (type.equals("search"))
      search();
    else if (type.equals("download")) {
      download();
    } else if (type.equals("home")) {
      home();
    }

  }

  public void search() {

    if (!mLT.containsKey(loader)) {
      ErrorHelper.errorJson("Warning Loader Not Recognized");
      return;
    }
    String url = baseURL + "/v1/mods/search";
    String queries = "?gameId=" + id;
    queries += "&gameVersion=" + version;
    queries += "&searchFilter=" + URLEncoder.encode(modName, StandardCharsets.UTF_8);
    queries += "&modLoaderType=" + mLT.get(loader);
    queries += "&pageSize=10";
    url += queries;

    HttpRequest searching = HttpRequest
        .newBuilder()
        .uri(URI.create(url))
        .header("x-api-key", API)
        .header("Accept", "application/json")
        .GET()
        .build();

    JsonObject response = JsonParser.parseString(NetworkUtils.attemptS(searching).body()).getAsJsonObject();
    Communicator.printer(sTranslator(response, true));

  }

  public void home() {

    String url = baseURL + "/v1/mods/featured";

    JsonObject request = new JsonObject();
    request.addProperty("gameId", id);

    HttpRequest homeing = HttpRequest
        .newBuilder()
        .uri(URI.create(url))
        .header("x-api-key", API)
        .header("Accept", "application/json")
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(request.toString()))
        .build();

    JsonObject response = JsonParser.parseString(NetworkUtils.attemptS(homeing).body()).getAsJsonObject();
    Communicator.printer(sTranslator(response, false));

  }

  private JsonObject sTranslator(JsonObject x, boolean mode) {

    JsonObject response = new JsonObject();
    response.addProperty("status", 0);
    response.addProperty("type", "query");
    JsonArray mods = new JsonArray();

    for (int i = 0; i < x.get((mode) ? "data" : "featured").getAsJsonArray().size(); i++) {
      JsonArray mod = new JsonArray();
      mod.add(x.get((mode) ? "data" : "featured").getAsJsonArray().get(i).getAsJsonObject().get("id"));
      mod.add(x.get((mode) ? "data" : "featured").getAsJsonArray().get(i).getAsJsonObject().get("name"));
      mods.add(mod);
    }
    response.add("mods", mods);
    return response;

  }

  public void download() {
    String url = baseURL + "/v1/mods/" + modId + "/files?modId=" + modId + "&gameVersion=" + version + "&modLoaderType="
        + mLT.get(loader);

    HttpRequest downloading = HttpRequest
        .newBuilder()
        .uri(URI.create(url))
        .header("x-api-key", API)
        .header("Accept", "application/json")
        .GET()
        .build();

    String downloadURL, filename;
    try {
      JsonObject data = JsonParser.parseString(NetworkUtils.attemptS(downloading).body()).getAsJsonObject().get("data")
          .getAsJsonArray().get(0)
          .getAsJsonObject();

      if (data.get("downloadUrl").isJsonNull()) {
        ErrorHelper.errorJson("Author does not permit API downloads.");
        return;
      } else {
        downloadURL = data.get("downloadUrl").getAsString();
        filename = data.get("fileName").getAsString();
      }

    } catch (Exception e) {
      ErrorHelper.errorJson("File does not exist");
      return;
    }

    HttpRequest downloaded = HttpRequest
        .newBuilder()
        .uri(URI.create(downloadURL))
        .header("x-api-key", API)
        .GET()
        .build();

    HttpResponse<InputStream> downloadRequest = NetworkUtils.attemptI(downloaded);

    if (downloadRequest.statusCode() != 200) {
      ErrorHelper.errorJson("Website returned status code: " + downloadRequest.statusCode());
      return;
    }

    long filesize = downloadRequest.headers().firstValueAsLong("content-length").orElse(-1L);

    NetworkUtils.prog(downloadRequest.body(), "mods/" + filename, filesize);

  }
}
