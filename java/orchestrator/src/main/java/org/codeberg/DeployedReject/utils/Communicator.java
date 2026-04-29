package org.codeberg.DeployedReject.utils;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import com.google.gson.JsonObject;

public class Communicator implements Runnable {

  public static BlockingQueue<JsonObject> printBuffer = new LinkedBlockingQueue<>();

  @Override
  public void run() {

    while (true) {
      try {
        System.out.println(printBuffer.take());
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  }

  public static void printer(JsonObject x) {
    printBuffer.add(x);
  }

  public static void startPrinter() {
    Thread printing = new Thread(new Communicator());
    printing.setDaemon(true);
    printing.start();
  }

}
