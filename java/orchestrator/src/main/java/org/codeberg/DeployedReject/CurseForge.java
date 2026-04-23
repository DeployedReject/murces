package org.codeberg.DeployedReject;

import java.io.FileOutputStream;
import java.io.InputStream;
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
    url += queries;

    HttpRequest searching = HttpRequest
        .newBuilder()
        .uri(URI.create(url))
        .header("x-api-key", API)
        .header("Accept", "application/json")
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

    HttpClient device = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();
    HttpResponse<String> temp;
    try {
      temp = device.send(downloading, BodyHandlers.ofString());

      if (temp.statusCode() != 200) {
        ErrorHelper.errorJson("Website returned: " + temp.statusCode() + "\nI think you sent a bad id");
        return;
      }

    } catch (Exception e) {
      ErrorHelper.errorJson(e.toString());
      return;
    }

    String downloadURL, filename;
    try {
      JsonObject data = JsonParser.parseString(temp.body()).getAsJsonObject().get("data").getAsJsonArray().get(0)
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

    HttpResponse<InputStream> downloadRequest;

    try {
      downloadRequest = device.send(downloaded, BodyHandlers.ofInputStream());

    } catch (Exception e) {
      ErrorHelper.errorJson(e.toString());
      return;
    }

    if (downloadRequest.statusCode() != 200) {
      ErrorHelper.errorJson("Website returned status code: " + downloadRequest.statusCode());
      return;
    }

    try {
      FileOutputStream downloadedFile = new FileOutputStream(filename);

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
        Communicator.printer(response);
      }

      downloadedFile.close();
    } catch (Exception e) {
      ErrorHelper.errorJson(e.toString());
    }
  }
}
