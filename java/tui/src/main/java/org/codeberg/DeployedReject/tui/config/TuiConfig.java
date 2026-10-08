package org.codeberg.DeployedReject.tui.config;

import java.util.LinkedHashMap;
import java.util.Map;

public class TuiConfig {

    public static class ServerProfile {
        private String name = "default";
        private String directory = ".";
        private String sessionName = "mcsv";
        private String gameVersion = "1.21.1";
        private String loader = "fabric";
        private String ram = "4G";
        private int port = 25565;

        public ServerProfile() {}

        public ServerProfile(String name, String directory, String sessionName, String gameVersion, String loader, String ram, int port) {
            this.name = name;
            this.directory = directory;
            this.sessionName = sessionName;
            this.gameVersion = gameVersion;
            this.loader = loader;
            this.ram = ram;
            this.port = port;
        }

        public String getName() {
            return name != null && !name.trim().isEmpty() ? name.trim() : "default";
        }

        public void setName(String name) {
            if (name != null && !name.trim().isEmpty()) {
                String clean = name.trim().replaceAll("[^a-zA-Z0-9_.-]", "_");
                this.name = clean;
                this.sessionName = clean;
            }
        }

        public String getDirectory() {
            return directory != null && !directory.trim().isEmpty() ? directory.trim() : ".";
        }

        public void setDirectory(String directory) {
            this.directory = directory;
        }

        public String getSessionName() {
            return sessionName != null && !sessionName.trim().isEmpty() ? sessionName.trim() : "mcsv";
        }

        public void setSessionName(String sessionName) {
            if (sessionName != null && !sessionName.trim().isEmpty()) {
                String clean = sessionName.trim().replaceAll("[^a-zA-Z0-9_.-]", "_");
                this.sessionName = clean;
                this.name = clean;
            }
        }

        public String getGameVersion() {
            return gameVersion != null && !gameVersion.trim().isEmpty() ? gameVersion.trim() : "1.21.1";
        }

        public void setGameVersion(String gameVersion) {
            this.gameVersion = gameVersion;
        }

        public String getLoader() {
            return loader != null && !loader.trim().isEmpty() ? loader.trim() : "fabric";
        }

        public void setLoader(String loader) {
            this.loader = loader;
        }

        public String getRam() {
            return ram != null && !ram.trim().isEmpty() ? ram.trim() : "4G";
        }

        public void setRam(String ram) {
            this.ram = ram;
        }

        public int getPort() {
            return port > 0 ? port : 25565;
        }

        public void setPort(int port) {
            this.port = port;
        }
    }

    private String theme = "Gruvbox Dark";
    private int transparencyPercent = 0;
    private boolean trueColor = true;
    private boolean enforceMinSize = true;
    private boolean pickaxeAnimation = true;
    private String nerdFontMode = "auto";

    private String sessionName = "mcsv";
    private String serverDir = ".";
    private String activeServer = "default";
    private Map<String, ServerProfile> serverProfiles = new LinkedHashMap<>();

    private boolean portableJdk = false;
    private String gameVersion = "1.21.1";
    private String loader = "fabric";

    private String backupSourceFolder = "world";
    private String backupTargetFolder = "backup";
    private int backupRetentionLimit = 3;
    private boolean backupCloudSync = false;
    private String backupCloudRemote = "minecraftdrive";

    public TuiConfig() {
        if (serverProfiles.isEmpty()) {
            serverProfiles.put("default", new ServerProfile("default", ".", "mcsv", "1.21.1", "fabric", "4G", 25565));
        }
    }

    public String getTheme() {
        return theme != null && !theme.trim().isEmpty() ? theme : "Gruvbox Dark";
    }

    public void setTheme(String theme) {
        this.theme = theme;
    }

    public int getTransparencyPercent() {
        return Math.max(0, Math.min(100, transparencyPercent));
    }

    public void setTransparencyPercent(int transparencyPercent) {
        this.transparencyPercent = Math.max(0, Math.min(100, transparencyPercent));
    }

    public boolean isTrueColor() {
        return trueColor;
    }

    public void setTrueColor(boolean trueColor) {
        this.trueColor = trueColor;
    }

    public boolean isEnforceMinSize() {
        return enforceMinSize;
    }

    public void setEnforceMinSize(boolean enforceMinSize) {
        this.enforceMinSize = enforceMinSize;
    }

    public boolean isPickaxeAnimation() {
        return pickaxeAnimation;
    }

    public void setPickaxeAnimation(boolean pickaxeAnimation) {
        this.pickaxeAnimation = pickaxeAnimation;
    }

    public String getNerdFontMode() {
        return nerdFontMode != null && !nerdFontMode.trim().isEmpty() ? nerdFontMode : "auto";
    }

    public void setNerdFontMode(String nerdFontMode) {
        this.nerdFontMode = nerdFontMode;
    }

    public String getSessionName() {
        return sessionName != null && !sessionName.trim().isEmpty() ? sessionName.trim() : "mcsv";
    }

    public void setSessionName(String sessionName) {
        setServerName(sessionName);
    }

    public String getServerName() {
        return getSessionName();
    }

    public void setServerName(String name) {
        if (name == null || name.trim().isEmpty()) {
            name = "mcsv";
        }
        String clean = name.trim().replaceAll("[^a-zA-Z0-9_.-]", "_");
        this.sessionName = clean;
        this.activeServer = clean;
        ServerProfile profile = getActiveServerProfile();
        if (profile != null) {
            profile.setName(clean);
            profile.setSessionName(clean);
        }
    }

    public String getServerDir() {
        return serverDir != null && !serverDir.trim().isEmpty() ? serverDir.trim() : ".";
    }

    public void setServerDir(String serverDir) {
        if (serverDir != null && !serverDir.trim().isEmpty()) {
            this.serverDir = serverDir.trim();
        }
    }

    public String getActiveServer() {
        return activeServer != null && !activeServer.trim().isEmpty() ? activeServer.trim() : "default";
    }

    public void setActiveServer(String activeServer) {
        if (activeServer != null && !activeServer.trim().isEmpty()) {
            this.activeServer = activeServer.trim();
        }
    }

    public Map<String, ServerProfile> getServerProfiles() {
        if (serverProfiles == null) {
            serverProfiles = new LinkedHashMap<>();
        }
        if (serverProfiles.isEmpty()) {
            serverProfiles.put("default", new ServerProfile("default", serverDir != null ? serverDir : ".", sessionName != null ? sessionName : "mcsv", gameVersion, loader, "4G", 25565));
        }
        return serverProfiles;
    }

    public void setServerProfiles(Map<String, ServerProfile> serverProfiles) {
        if (serverProfiles != null) {
            this.serverProfiles = serverProfiles;
        }
    }

    public ServerProfile getActiveServerProfile() {
        Map<String, ServerProfile> map = getServerProfiles();
        ServerProfile profile = map.get(getActiveServer());
        if (profile == null) {
            profile = new ServerProfile(getActiveServer(), getServerDir(), getSessionName(), getGameVersion(), getLoader(), "4G", 25565);
            map.put(getActiveServer(), profile);
        }
        return profile;
    }

    public void addServerProfile(ServerProfile profile) {
        if (profile != null && profile.getName() != null) {
            getServerProfiles().put(profile.getName(), profile);
        }
    }

    public void switchServer(String name) {
        if (name != null && getServerProfiles().containsKey(name)) {
            setActiveServer(name);
            ServerProfile profile = getServerProfiles().get(name);
            setSessionName(profile.getSessionName());
            setServerDir(profile.getDirectory());
            setGameVersion(profile.getGameVersion());
            setLoader(profile.getLoader());
        }
    }

    public boolean isPortableJdk() {
        return portableJdk;
    }

    public void setPortableJdk(boolean portableJdk) {
        this.portableJdk = portableJdk;
    }

    public String getGameVersion() {
        return gameVersion != null && !gameVersion.trim().isEmpty() ? gameVersion.trim() : "1.21.1";
    }

    public void setGameVersion(String gameVersion) {
        if (gameVersion != null && !gameVersion.trim().isEmpty()) {
            this.gameVersion = gameVersion.trim();
        }
    }

    public String getLoader() {
        return loader != null && !loader.trim().isEmpty() ? loader.trim() : "fabric";
    }

    public void setLoader(String loader) {
        if (loader != null && !loader.trim().isEmpty()) {
            this.loader = loader.trim();
        }
    }

    public String getBackupSourceFolder() {
        return backupSourceFolder != null && !backupSourceFolder.trim().isEmpty() ? backupSourceFolder.trim() : "world";
    }

    public void setBackupSourceFolder(String backupSourceFolder) {
        this.backupSourceFolder = backupSourceFolder != null && !backupSourceFolder.trim().isEmpty() ? backupSourceFolder.trim() : "world";
    }

    public String getBackupTargetFolder() {
        return backupTargetFolder != null && !backupTargetFolder.trim().isEmpty() ? backupTargetFolder.trim() : "backup";
    }

    public void setBackupTargetFolder(String backupTargetFolder) {
        this.backupTargetFolder = backupTargetFolder != null && !backupTargetFolder.trim().isEmpty() ? backupTargetFolder.trim() : "backup";
    }

    public int getBackupRetentionLimit() {
        return backupRetentionLimit > 0 ? backupRetentionLimit : 3;
    }

    public void setBackupRetentionLimit(int backupRetentionLimit) {
        this.backupRetentionLimit = backupRetentionLimit > 0 ? backupRetentionLimit : 3;
    }

    public boolean isBackupCloudSync() {
        return backupCloudSync;
    }

    public void setBackupCloudSync(boolean backupCloudSync) {
        this.backupCloudSync = backupCloudSync;
    }

    public String getBackupCloudRemote() {
        return backupCloudRemote != null && !backupCloudRemote.trim().isEmpty() ? backupCloudRemote.trim() : "minecraftdrive";
    }

    public void setBackupCloudRemote(String backupCloudRemote) {
        this.backupCloudRemote = backupCloudRemote != null && !backupCloudRemote.trim().isEmpty() ? backupCloudRemote.trim() : "minecraftdrive";
    }
}
