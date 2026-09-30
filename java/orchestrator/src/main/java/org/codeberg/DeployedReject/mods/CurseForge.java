package org.codeberg.DeployedReject.mods;

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
import org.codeberg.DeployedReject.utils.ErrorHelper;
import org.codeberg.DeployedReject.ModAPI;
import org.codeberg.DeployedReject.utils.Communicator;
import org.codeberg.DeployedReject.utils.NetworkUtils;

public class CurseForge implements ModAPI {

  private String type;
  private int id = 432;
  private String modName;
  private String version;
  private String loader;
  private String API;
  private String modId;

  public CurseForge(String type, String modName, String version, String loader, String API, String modId) {
    this.type = type;
    this.modName = modName;
    this.version = version;
    this.loader = loader;
    this.API = API;
    this.modId = modId;
  }

  private static HashMap<String, Integer> mLT = new HashMap<>(); // Mod Loader Translator

  private static final String baseURL = "https://api.curseforge.com";
  private static final String searchURL = baseURL + "/v1/mods/search";
  private static final String homeURL = baseURL + "/v1/mods/featured";

  @Override
  public void handler() {
    mLT.put("forge", 1);
    mLT.put("fabric", 4);
    mLT.put("liteLoader", 3);
    mLT.put("cauldron", 2);
    mLT.put("quilt", 5);
    mLT.put("neoForge", 6);

    switch (type) {
      case "search":
        search();
        break;
      case "download":
        download();
        break;
      case "home":
        home();
        break;
      default:
        ErrorHelper.errorJson("Operation not supported.");
    }
  }

  private void search() {

    if (!mLT.containsKey(loader)) {
      ErrorHelper.errorJson("Warning Loader Not Recognized");
      return;
    }
    String url = searchURL;
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

    HttpResponse<String> tempR = NetworkUtils.attemptS(searching);
    if (tempR == null)
      return;

    JsonObject response = JsonParser.parseString(tempR.body()).getAsJsonObject();

    Communicator.printer(sTranslator(response, true));

  }

  private void home() {

    String url = homeURL;

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

    HttpResponse<String> tempR = NetworkUtils.attemptS(homeing);
    if (tempR == null) {
      return;
    }
    JsonObject response = JsonParser.parseString(tempR.body()).getAsJsonObject();
    Communicator.printer(sTranslator(response, false));

  }

  private JsonObject sTranslator(JsonObject x, boolean mode) {

    JsonObject response = new JsonObject();
    response.addProperty("status", 0);
    response.addProperty("type", "query");
    JsonArray mods = new JsonArray();

    try {
      JsonArray targetArray;

      if (mode) {
        targetArray = x.get("data").getAsJsonArray();
      } else {
        targetArray = x.get("data").getAsJsonObject().get("popular").getAsJsonArray();
      }

      for (int i = 0; i < targetArray.size(); i++) {
        JsonArray mod = new JsonArray();
        JsonObject currentMod = targetArray.get(i).getAsJsonObject();

        mod.add(currentMod.has("id") ? currentMod.get("id").getAsString() : "");
        mod.add(currentMod.has("name") ? currentMod.get("name").getAsString() : "");
        String author = "";
        if (currentMod.has("authors") && currentMod.get("authors").isJsonArray() && currentMod.getAsJsonArray("authors").size() > 0) {
            JsonObject firstAuthor = currentMod.getAsJsonArray("authors").get(0).getAsJsonObject();
            if (firstAuthor.has("name") && !firstAuthor.get("name").isJsonNull()) {
                author = firstAuthor.get("name").getAsString();
            }
        }
        mod.add(author);
        String summary = currentMod.has("summary") && !currentMod.get("summary").isJsonNull() ? currentMod.get("summary").getAsString() : "";
        mod.add(summary);
        mods.add(mod);
      }

    } catch (Exception e) {
      ErrorHelper.errorJson("API Fucking CHANGED");
      return response;
    }

    response.add("mods", mods);
    return response;
  }

  private void download() {
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
      HttpResponse<String> tempR = NetworkUtils.attemptS(downloading);
      if (tempR == null) {
        return;
      }
      JsonObject data = JsonParser.parseString(tempR.body()).getAsJsonObject().get("data")
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

    if (downloadRequest == null)
      return;

    if (downloadRequest.statusCode() != 200) {
      ErrorHelper.errorJson("Website returned status code: " + downloadRequest.statusCode());
      return;
    }

    long filesize = downloadRequest.headers().firstValueAsLong("content-length").orElse(-1L);

    NetworkUtils.prog(downloadRequest.body(), "mods/" + filename, filesize);

  }
}
