package org.codeberg.DeployedReject.tui.backend;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.IOException;
import java.io.FileReader;
import java.net.URI;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Resolves the identity the server uses, rather than always inventing an offline UUID. */
final class PlayerIdentityResolver {
    interface ProfileLookup {
        PlayerAccessManager.Entry lookup(String name) throws IOException;
    }

    static PlayerAccessManager.Entry resolve(File directory, String input, boolean online) throws IOException {
        return resolve(directory, input, online, PlayerIdentityResolver::lookupOnline);
    }

    static PlayerAccessManager.Entry resolve(File directory, String input, boolean online,
                                             ProfileLookup lookup) throws IOException {
        String value = input.trim();
        boolean explicitUuid = value.matches("(?i)[0-9a-f]{32}|[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
        if (!explicitUuid && !value.matches("[A-Za-z0-9_]{1,16}")) {
            throw new IOException("Enter a Minecraft Java username or UUID. For proxy/Bedrock players, use their server UUID.");
        }
        String uuid = explicitUuid ? normalizeUuid(value) : null;
        File cache = new File(directory, "usercache.json");
        if (cache.isFile()) {
            try (FileReader reader = new FileReader(cache, StandardCharsets.UTF_8)) {
                for (JsonElement element : JsonParser.parseReader(reader).getAsJsonArray()) {
                    JsonObject profile = element.getAsJsonObject();
                    String name = profile.get("name").getAsString();
                    String cachedUuid = normalizeUuid(profile.get("uuid").getAsString());
                    if (explicitUuid ? cachedUuid.equals(uuid) : name.equalsIgnoreCase(value)) {
                        // Do not reuse an offline identity after switching online-mode on.
                        if (!explicitUuid && online && UUID.fromString(cachedUuid).version() != 4) continue;
                        return new PlayerAccessManager.Entry(name, cachedUuid);
                    }
                }
            } catch (RuntimeException e) {
                throw new IOException("Cannot read player identities from " + cache, e);
            }
        }
        if (explicitUuid) return new PlayerAccessManager.Entry(uuid, uuid);
        if (online) return lookup.lookup(value); // Never silently fall back to an offline UUID.
        // Case is significant in Minecraft's offline UUID algorithm.
        return new PlayerAccessManager.Entry(value, UUID.nameUUIDFromBytes(
            ("OfflinePlayer:" + value).getBytes(StandardCharsets.UTF_8)).toString());
    }

    private static String normalizeUuid(String value) {
        String raw = value.replace("-", "");
        if (!raw.matches("(?i)[0-9a-f]{32}")) throw new IllegalArgumentException("Invalid player UUID");
        return UUID.fromString(raw.substring(0, 8) + "-" + raw.substring(8, 12) + "-"
            + raw.substring(12, 16) + "-" + raw.substring(16, 20) + "-" + raw.substring(20)).toString();
    }

    private static PlayerAccessManager.Entry lookupOnline(String name) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) URI.create(
            "https://api.minecraftservices.com/minecraft/profile/lookup/name/" + name).toURL().openConnection();
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        try {
            if (connection.getResponseCode() != 200) {
                throw new IOException("Cannot resolve online UUID for " + name
                    + " (HTTP " + connection.getResponseCode() + "). Use the player's UUID or try again later.");
            }
            try (var reader = new java.io.InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8)) {
                JsonObject profile = JsonParser.parseReader(reader).getAsJsonObject();
                return new PlayerAccessManager.Entry(profile.get("name").getAsString(),
                    normalizeUuid(profile.get("id").getAsString()));
            } catch (RuntimeException e) {
                throw new IOException("Invalid online profile for " + name, e);
            }
        } finally {
            connection.disconnect();
        }
    }
}
