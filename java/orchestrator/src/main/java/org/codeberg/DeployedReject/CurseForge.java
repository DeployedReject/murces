package org.codeberg.DeployedReject;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;

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

  String baseURL = "https://api.curseforge.com";

  public void curseForgeHandler() {
    if (type.equals("search"))
      search();
    else if (type.equals("download")) {
      // To Do
    } else if (type.equals("home")) {
      home();
    }

  }

  public void search() {
    // Mod Loader Translator
    HashMap<String, Integer> mLT = new HashMap<>();
    mLT.put("forge", 1);
    mLT.put("fabric", 4);
    mLT.put("liteLoader", 3);
    mLT.put("cauldron", 2);
    mLT.put("quilt", 5);
    mLT.put("neoForge", 6);

    String url = baseURL + "/v1/mods/search";
    String queries = "?gameId=" + id;
    queries += "&gameVersion=" + version;
    queries += "&searchFilter=" + URLEncoder.encode(modName, StandardCharsets.UTF_8);
    queries += "&modLoaderType=" + mLT.get(loader);
    url += queries;

    HttpRequest searching = HttpRequest
        .newBuilder()
        .uri(URI.create(url))
        .header("x-api-key", API)
        .GET()
        .build();

    HttpClient device = HttpClient.newHttpClient();

    try {
      HttpResponse<String> result = device.send(searching, BodyHandlers.ofString());

      if (result.statusCode() != 200) {
        ErrorHelper.errorJson("Website returned status code: " + result.statusCode());
        return;
      }
      JsonObject response = JsonParser.parseString(result.body()).getAsJsonObject();
      Communicator.printer(response);
    } catch (Exception e) {
      ErrorHelper.errorJson(e.toString());
    }

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

    HttpClient device = HttpClient.newHttpClient();

    try {
      HttpResponse<String> home = device.send(homeing, BodyHandlers.ofString());

      if (home.statusCode() != 200) {
        ErrorHelper.errorJson("Website returned status code: " + home.statusCode());
        return;
      }

      JsonObject response = JsonParser.parseString(home.body()).getAsJsonObject();
      Communicator.printer(response);

    } catch (Exception e) {
      ErrorHelper.errorJson(e.toString());
    }

  }
}
