package org.codeberg.DeployedReject.device;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.codeberg.DeployedReject.utils.Communicator;
import org.codeberg.DeployedReject.utils.ErrorHelper;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class Tunnel {

    private static final String API_BASE = "https://api.playit.gg";
    private static final String AGENT_FILE = "playitagent.txt";
    private static final String CONFIG_FILE = "playit.toml";
    private static final String TUNNELS_STORE = "tunnels.json";

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private static JsonObject post(String endpoint, JsonObject body, String agentKey) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(API_BASE + endpoint))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json");

        if (agentKey != null && !agentKey.isEmpty()) {
            builder.header("Authorization", "Agent-Key " + agentKey);
        }

        String bodyStr = body != null ? body.toString() : "{}";
        builder.POST(HttpRequest.BodyPublishers.ofString(bodyStr, StandardCharsets.UTF_8));

        HttpResponse<String> resp = HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        String respBody = resp.body();
        if (respBody == null || respBody.trim().isEmpty()) {
            return new JsonObject();
        }
        return JsonParser.parseString(respBody).getAsJsonObject();
    }

    public static void setup() {
        File agentKeyFile = new File(AGENT_FILE);
        if (agentKeyFile.exists() && agentKeyFile.length() > 0) {
            JsonObject existsResp = new JsonObject();
            existsResp.addProperty("status", 0);
            existsResp.addProperty("type", "tunnel");
            existsResp.addProperty("action", "setup");
            existsResp.addProperty("message", "Agent already exists.");
            Communicator.printer(existsResp);
            return;
        }

        String claimCode = UUID.randomUUID().toString().replace("-", "").substring(0, 12);

        JsonObject reqSetup = new JsonObject();
        reqSetup.addProperty("code", claimCode);
        reqSetup.addProperty("agent_type", "assignable");
        reqSetup.addProperty("version", "0.15.26");

        try {
            JsonObject res = post("/claim/setup", reqSetup, null);

            if (res.has("status") && "success".equals(res.get("status").getAsString())) {
                JsonObject setupMsg = new JsonObject();
                setupMsg.addProperty("status", 0);
                setupMsg.addProperty("type", "tunnel");
                setupMsg.addProperty("action", "setup");
                setupMsg.addProperty("code", claimCode);
                setupMsg.addProperty("url", "https://playit.gg/claim/" + claimCode);
                Communicator.printer(setupMsg);

                CompletableFuture.runAsync(() -> pollClaim(claimCode));
            } else {
                ErrorHelper.errorJson("Unexpected claim setup response: " + res);
            }

        } catch (Exception e) {
            ErrorHelper.errorJson("Failed claim setup: " + e.getMessage());
        }
    }

    private static void pollClaim(String code) {
        JsonObject reqSetup = new JsonObject();
        reqSetup.addProperty("code", code);
        reqSetup.addProperty("agent_type", "assignable");
        reqSetup.addProperty("version", "0.15.26");

        JsonObject reqExchange = new JsonObject();
        reqExchange.addProperty("code", code);

        int attempts = 0;
        int maxAttempts = 180;

        while (attempts < maxAttempts) {
            try {
                Thread.sleep(2000);
                attempts++;

                JsonObject setupRes = null;
                try {
                    setupRes = post("/claim/setup", reqSetup, null);
                } catch (Exception ignored) {
                }

                if (setupRes != null && setupRes.has("data") && setupRes.get("data").isJsonPrimitive()) {
                    String state = setupRes.get("data").getAsString();
                    if ("UserRejected".equalsIgnoreCase(state)) {
                        ErrorHelper.errorJson("Playit claim was rejected by user.");
                        return;
                    }
                }

                boolean shouldTryExchange = false;
                if (setupRes != null && setupRes.has("data") && setupRes.get("data").isJsonPrimitive()) {
                    String state = setupRes.get("data").getAsString();
                    if ("UserAccepted".equalsIgnoreCase(state)) {
                        shouldTryExchange = true;
                    }
                }
                if (attempts % 3 == 0) {
                    shouldTryExchange = true;
                }

                if (shouldTryExchange) {
                    try {
                        JsonObject exchangeRes = post("/claim/exchange", reqExchange, null);

                        if (exchangeRes.has("status") && "success".equals(exchangeRes.get("status").getAsString()) &&
                                exchangeRes.has("data") && exchangeRes.get("data").isJsonObject()) {

                            JsonObject dataObj = exchangeRes.getAsJsonObject("data");
                            if (dataObj.has("secret_key")) {
                                String secretKey = dataObj.get("secret_key").getAsString();

                                Files.writeString(Paths.get(AGENT_FILE), secretKey, StandardCharsets.UTF_8);
                                Files.writeString(Paths.get(CONFIG_FILE), "secret_key = \"" + secretKey + "\"\n", StandardCharsets.UTF_8);

                                try {
                                    JsonObject rundata = post("/agents/rundata", new JsonObject(), secretKey);
                                    if (rundata.has("data") && rundata.get("data").isJsonObject()) {
                                        JsonObject runDataObj = rundata.getAsJsonObject("data");
                                        JsonArray tunnelsArr = runDataObj.has("tunnels") && runDataObj.get("tunnels").isJsonArray()
                                                ? runDataObj.getAsJsonArray("tunnels") : new JsonArray();

                                        if (tunnelsArr.isEmpty()) {
                                            JsonObject createReq = new JsonObject();
                                            createReq.addProperty("name", "minecraft-server");
                                            createReq.addProperty("tunnel_type", "minecraft-java");
                                            createReq.addProperty("port_type", "tcp");
                                            createReq.addProperty("port_count", 1);
                                            createReq.addProperty("enabled", true);

                                            JsonObject origin = new JsonObject();
                                            origin.addProperty("type", "default");
                                            JsonObject originData = new JsonObject();
                                            originData.addProperty("local_ip", "127.0.0.1");
                                            originData.addProperty("local_port", 25565);
                                            origin.add("data", originData);
                                            createReq.add("origin", origin);

                                            post("/tunnels/create", createReq, secretKey);
                                        }
                                    }
                                } catch (Exception ignored) {
                                }

                                startDaemon();

                                JsonObject ok = new JsonObject();
                                ok.addProperty("status", 0);
                                ok.addProperty("type", "tunnel");
                                ok.addProperty("action", "claimed");
                                ok.addProperty("message", "Playit agent successfully linked and daemon started!");
                                Communicator.printer(ok);

                                status();
                                return;
                            }
                        }
                    } catch (Exception ignored) {
                    }
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }

        ErrorHelper.errorJson("Playit claim handshake timed out.");
    }

    public static boolean isDaemonRunning() {
        try {
            Process p = new ProcessBuilder(org.codeberg.DeployedReject.utils.Platform.getMultiplexer(), "has-session", "-t", "playit").start();
            return p.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    public static void startDaemon() {
        File agentKeyFile = new File(AGENT_FILE);
        if (!agentKeyFile.exists()) {
            ErrorHelper.errorJson("Cannot start Playit tunnel daemon: missing agent credentials.");
            return;
        }
        try {
            String secretKey = Files.readString(agentKeyFile.toPath(), StandardCharsets.UTF_8).trim();
            Files.writeString(Paths.get(CONFIG_FILE), "secret_key = \"" + secretKey + "\"\n", StandardCharsets.UTF_8);

            if (isDaemonRunning()) {
                JsonObject running = new JsonObject();
                running.addProperty("status", 0);
                running.addProperty("type", "tunnel");
                running.addProperty("action", "daemon");
                running.addProperty("running", true);
                running.addProperty("message", "Playit daemon is already active.");
                Communicator.printer(running);
                return;
            }

            String cmd = "playit start";
            if (org.codeberg.DeployedReject.utils.Platform.isWindows()) {
                if (new File("./playit.exe").exists()) {
                    cmd = ".\\playit.exe start";
                } else if (new File("./playitd.exe").exists()) {
                    cmd = ".\\playitd.exe --secret-path playit.toml --socket-path ./playit.sock";
                } else {
                    cmd = "playit start";
                }
            } else {
                if (new File("./playitd").canExecute()) {
                    cmd = "./playitd --secret-path playit.toml --socket-path ./playit.sock";
                } else if (new File("./playit").canExecute()) {
                    cmd = "./playit start";
                }
            }

            ProcessBuilder pb = new ProcessBuilder(org.codeberg.DeployedReject.utils.Platform.getMultiplexer(), "new-session", "-d", "-s", "playit", cmd);
            pb.start().waitFor();

            JsonObject res = new JsonObject();
            res.addProperty("status", 0);
            res.addProperty("type", "tunnel");
            res.addProperty("action", "daemon");
            res.addProperty("running", true);
            res.addProperty("message", "Playit tunnel daemon started in background.");
            Communicator.printer(res);
        } catch (Exception e) {
            ErrorHelper.errorJson("Failed starting Playit daemon: " + e.getMessage());
        }
    }

    public static void stopDaemon() {
        try {
            if (isDaemonRunning()) {
                Process p = new ProcessBuilder(org.codeberg.DeployedReject.utils.Platform.getMultiplexer(), "kill-session", "-t", "playit").start();
                p.waitFor();
            }
            Files.deleteIfExists(Paths.get("playit.sock"));
            JsonObject res = new JsonObject();
            res.addProperty("status", 0);
            res.addProperty("type", "tunnel");
            res.addProperty("action", "daemon");
            res.addProperty("running", false);
            res.addProperty("message", "Playit tunnel daemon stopped.");
            Communicator.printer(res);
        } catch (Exception e) {
            ErrorHelper.errorJson("Failed stopping Playit daemon: " + e.getMessage());
        }
    }

    public static void status() {
        File agentKeyFile = new File(AGENT_FILE);
        if (!agentKeyFile.exists()) {
            JsonObject notFound = new JsonObject();
            notFound.addProperty("status", 0);
            notFound.addProperty("type", "tunnel");
            notFound.addProperty("action", "status");
            notFound.addProperty("linked", false);
            notFound.addProperty("running", isDaemonRunning());
            Communicator.printer(notFound);
            return;
        }

        try {
            String secretKey = Files.readString(agentKeyFile.toPath(), StandardCharsets.UTF_8).trim();
            JsonObject rundata = post("/agents/rundata", new JsonObject(), secretKey);

            JsonObject res = new JsonObject();
            res.addProperty("status", 0);
            res.addProperty("type", "tunnel");
            res.addProperty("action", "status");
            res.addProperty("linked", true);
            res.addProperty("running", isDaemonRunning());

            JsonArray tunnelsArr = new JsonArray();
            if (rundata.has("data") && rundata.get("data").isJsonObject()) {
                JsonObject dataObj = rundata.getAsJsonObject("data");
                if (dataObj.has("tunnels") && dataObj.get("tunnels").isJsonArray()) {
                    for (JsonElement el : dataObj.getAsJsonArray("tunnels")) {
                        if (el.isJsonObject()) {
                            JsonObject t = el.getAsJsonObject();
                            JsonObject item = new JsonObject();
                            item.addProperty("id", t.has("id") ? t.get("id").getAsString() : "");

                            if (t.has("local_port")) {
                                item.addProperty("localPort", t.get("local_port").getAsInt());
                            } else if (t.has("localPort")) {
                                item.addProperty("localPort", t.get("localPort").getAsInt());
                            }

                            item.addProperty("proto", t.has("proto") ? t.get("proto").getAsString() : "tcp");

                            String domain = "";
                            if (t.has("custom_domain") && !t.get("custom_domain").isJsonNull()) {
                                domain = t.get("custom_domain").getAsString();
                            }
                            if (domain.isEmpty() && t.has("assigned_domain") && !t.get("assigned_domain").isJsonNull()) {
                                domain = t.get("assigned_domain").getAsString();
                            }
                            if (!domain.isEmpty()) {
                                item.addProperty("publicAddress", domain);
                            }
                            tunnelsArr.add(item);
                        }
                    }
                }
            }

            res.add("tunnels", tunnelsArr);
            Communicator.printer(res);

            Files.writeString(Paths.get(TUNNELS_STORE), tunnelsArr.toString(), StandardCharsets.UTF_8);

        } catch (Exception e) {
            ErrorHelper.errorJson("Failed to query playit agent rundata: " + e.getMessage());
        }
    }

    public static void reset() {
        try {
            stopDaemon();
            Files.deleteIfExists(Paths.get(AGENT_FILE));
            Files.deleteIfExists(Paths.get(CONFIG_FILE));
            Files.deleteIfExists(Paths.get(TUNNELS_STORE));

            JsonObject res = new JsonObject();
            res.addProperty("status", 0);
            res.addProperty("type", "tunnel");
            res.addProperty("action", "reset");
            res.addProperty("message", "Playit agent credentials and tunnel configurations cleared.");
            Communicator.printer(res);
        } catch (Exception e) {
            ErrorHelper.errorJson("Failed resetting agent credentials: " + e.getMessage());
        }
    }
}
