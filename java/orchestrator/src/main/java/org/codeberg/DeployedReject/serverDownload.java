package org.codeberg.DeployedReject;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class serverDownload {
  public static String gameVersion;
  public static String serverVersion;
  public static String serverType;
  public static String type;

  public static void serverHandler() {
    if (serverType.equals("fabric")) {
      fabric();
    } else if (serverType.equals("forge")) {
      // To do
    } else {
      errorHelper.errorJson("Invalid Server Type");
    }
  }

  public static void fabric() {
    if (type.equals("homepage")) {
      String url = "https://meta.fabricmc.net/v2/versions/game";
      HttpRequest homepaging = HttpRequest.newBuilder()
          .uri(URI.create(url))
          .GET()
          .build();// Dont judge my naming sense.
      HttpClient device = HttpClient.newHttpClient();
      try {
        HttpResponse<String> results = device.send(homepaging, BodyHandlers.ofString());
        JsonArray temp = JsonParser.parseString(results.body()).getAsJsonArray();

        JsonArray response = new JsonArray();

        for (int i = 0; i < 10; i++) {
          response.add(temp.get(i));
        }

        System.out.println(response);
      } catch (Exception e) {
        errorHelper.errorJson(e.toString());
      }

    }
  }
}
