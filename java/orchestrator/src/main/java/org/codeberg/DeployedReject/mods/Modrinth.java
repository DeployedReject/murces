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
import org.codeberg.DeployedReject.utils.Communicator;

public class Modrinth {
  public String type;
  public String query = "mods";
  public String version;
  public String loader;
  public String email;

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
    HttpResponse<String> result = NetworkUtils.attemptS(downloading);

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

    HttpResponse<String> result = NetworkUtils.attemptS(searching);
    JsonObject response = JsonParser.parseString(result.body()).getAsJsonObject();
    Communicator.printer(sTranslator(response));

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
