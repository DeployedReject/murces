package org.codeberg.DeployedReject.tui.backend;

import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import org.codeberg.DeployedReject.tui.config.ConfigManager;

/** Run with java -ea after compiling against the TUI dependencies. No network or server required. */
public final class PlayerIdentityResolverTest {
    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("murces-identity-");
        String notch = "069a79f4-44e9-4726-a5be-fca90e38aaf5";
        var never = (PlayerIdentityResolver.ProfileLookup) name -> { throw new AssertionError("Unexpected lookup"); };
        assert PlayerIdentityResolver.resolve(dir.toFile(), "Notch", false, never).uuid
            .equals("b50ad385-829d-3141-a216-7e7d7539ba7f");
        assert !PlayerIdentityResolver.resolve(dir.toFile(), "notch", false, never).uuid
            .equals("b50ad385-829d-3141-a216-7e7d7539ba7f");
        assert PlayerIdentityResolver.resolve(dir.toFile(), notch.replace("-", ""), true, never).uuid.equals(notch);
        var online = PlayerIdentityResolver.resolve(dir.toFile(), "notch", true,
            name -> new PlayerAccessManager.Entry("Notch", notch));
        assert online.name.equals("Notch") && online.uuid.equals(notch);
        try {
            PlayerIdentityResolver.resolve(dir.toFile(), "Notch", true,
                name -> { throw new IOException("API unavailable"); });
            throw new AssertionError("Online lookup failure must not generate an offline UUID");
        } catch (IOException expected) { }
        Files.writeString(dir.resolve("usercache.json"),
            "[{\"name\":\"Notch\",\"uuid\":\"" + notch + "\"}]");
        assert PlayerIdentityResolver.resolve(dir.toFile(), "notch", true, never).uuid.equals(notch);
        assert PlayerIdentityResolver.resolve(dir.toFile(), "notch", false, never).uuid.equals(notch);
        assert PlayerIdentityResolver.resolve(dir.toFile(), notch, true, never).name.equals("Notch");
        Files.writeString(dir.resolve("usercache.json"),
            "[{\"name\":\"Notch\",\"uuid\":\"b50ad385-829d-3141-a216-7e7d7539ba7f\"}]");
        assert PlayerIdentityResolver.resolve(dir.toFile(), "notch", false, never).name.equals("Notch");
        assert PlayerIdentityResolver.resolve(dir.toFile(), "Notch", true,
            name -> new PlayerAccessManager.Entry("Notch", notch)).uuid.equals(notch);
        Files.writeString(dir.resolve("server.properties"), "online-mode=false\n");
        Files.writeString(dir.resolve("whitelist.json"),
            "[{\"name\":\"Notch\",\"uuid\":\"00000000-0000-0000-0000-000000000000\"}]");
        ConfigManager.getInstance().getConfig().setServerDir(dir.toString());
        ConfigManager.getInstance().getConfig().setSessionName("murces-test-no-server");
        PlayerAccessManager.addWhitelist("notch");
        var entries = PlayerAccessManager.getWhitelist();
        assert entries.size() == 1;
        assert entries.get(0).name.equals("Notch");
        assert entries.get(0).uuid.equals("b50ad385-829d-3141-a216-7e7d7539ba7f");
        PlayerAccessManager.addWhitelist("notch");
        assert PlayerAccessManager.getWhitelist().size() == 1;
        try {
            PlayerAccessManager.addWhitelist("Notch\nstop");
            throw new AssertionError("Invalid names must be rejected");
        } catch (IOException expected) { }
        System.out.println("Player identity and whitelist regression checks passed");
    }
}
