package org.codeberg.DeployedReject.utils;

import com.google.gson.JsonObject;

public class ErrorHelper {
  public static void errorJson(String error) {

    JsonObject response = new JsonObject();
    response.addProperty("status", 1);
    response.addProperty("error", error);
    Communicator.printer(response);
  }
}
