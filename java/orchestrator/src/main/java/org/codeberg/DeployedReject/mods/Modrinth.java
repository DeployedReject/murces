package org.codeberg.DeployedReject.mods;

import com.google.gson.JsonParser;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import java.io.InputStream;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URLEncoder;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.codeberg.DeployedReject.utils.ErrorHelper;
import org.codeberg.DeployedReject.utils.NetworkUtils;
import org.codeberg.DeployedReject.ModAPI;
import org.codeberg.DeployedReject.utils.Communicator;

public class Modrinth implements ModAPI {
  private String type;
  private String modName = "mods";
  private String version;
  private String loader;
  private String email;

  private static final String downloadURL = "https://api.modrinth.com/v2/project/";
  private static final String searchURL = "https://api.modrinth.com/v2/search";

  public Modrinth(String type, String modName, String version, String loader, String email) {
    this.type = type;
    this.modName = modName;
    this.version = version;
    this.loader = loader;
    this.email = email;
  }

  public void handler() {

    switch (type) {
      case "search":
        search(true);
        break;
      case "download":
        download();
        break;
      case "home":
        search(false);
        break;
      default:
        ErrorHelper.errorJson("Operation not supported.");
    }

  }

  private void download() {
    String url = downloadURL;
    loader = "[\"" + loader + "\"]";
    version = "[\"" + version + "\"]";

    loader = URLEncoder.encode(loader, StandardCharsets.UTF_8);
    version = URLEncoder.encode(version, StandardCharsets.UTF_8);

    url = url + modName + "/version?loaders=" + loader + "&game_versions=" + version;

    HttpRequest downloading = HttpRequest
        .newBuilder()
        .uri(URI.create(url))
        .header("User-Agent", "DeployedReject/MurCes/0.1 (" + email + ")")
        .GET()
        .build();
    HttpResponse<String> result = NetworkUtils.attemptS(downloading);
    if (result == null)
      return;

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
        // Getting it as a InputStream to monitor download progress.
        HttpResponse<InputStream> downloadRequest = NetworkUtils.attemptI(downloading);
        if (downloadRequest == null) {
          return;
        }

        long filesize = downloadRequest.headers().firstValueAsLong("content-length").orElse(-1L);
        NetworkUtils.prog(downloadRequest.body(), "mods/"
            + downloadLink.get("files").getAsJsonArray().get(0).getAsJsonObject().get("filename").getAsString(),
            filesize);

      } else {
        ErrorHelper.errorJson("No valid Link");
      }

    }
  }

  private void search(boolean mode) {

    String url = searchURL;
    String facets;
    if (mode) {

      facets = "[[\"project_type:mod\"],[ \"versions:" + version
          + "\"],[\"server_side:required\",\"server_side:optional\"],[\"categories:" + loader + "\"]]";
    } else {
      facets = "[[\"project_type:mod\"],[\"server_side:required\",\"server_side:optional\"]]";

    }
    modName = URLEncoder.encode(modName, StandardCharsets.UTF_8);
    facets = URLEncoder.encode(facets, StandardCharsets.UTF_8);
    url = url + "?query=" + modName + "&facets=" + facets + "&limit=10";

    HttpRequest searching = HttpRequest
        .newBuilder()
        .uri(URI.create(url))
        .header("User-Agent", "DeployedReject/MurCes/0.1 (" + email + ")")
        .GET()
        .build();

    HttpResponse<String> result = NetworkUtils.attemptS(searching);
    if (result == null)
      return;
    JsonObject response = JsonParser.parseString(result.body()).getAsJsonObject();
    Communicator.printer(sTranslator(response));

  }

  private JsonObject sTranslator(JsonObject x) {

    JsonObject response = new JsonObject();
    response.addProperty("status", 0);
    response.addProperty("type", "query");

    JsonArray mods = new JsonArray();

    for (int i = 0; i < x.get("hits").getAsJsonArray().size(); i++) {
      JsonObject hit = x.get("hits").getAsJsonArray().get(i).getAsJsonObject();
      JsonArray mod = new JsonArray();
      mod.add(hit.has("slug") && !hit.get("slug").isJsonNull() ? hit.get("slug").getAsString() : "");
      mod.add(hit.has("title") && !hit.get("title").isJsonNull() ? hit.get("title").getAsString() : "");
      mod.add(hit.has("author") && !hit.get("author").isJsonNull() ? hit.get("author").getAsString() : "");
      mod.add(hit.has("description") && !hit.get("description").isJsonNull() ? hit.get("description").getAsString() : "");
      mods.add(mod);
    }

    response.add("mods", mods);

    return response;

  }
}
