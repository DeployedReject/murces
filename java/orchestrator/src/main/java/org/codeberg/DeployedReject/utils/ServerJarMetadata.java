package org.codeberg.DeployedReject.utils;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Standardized server engine and version metadata embedded directly inside the server JAR archive
 * (META-INF/murces-server.json and META-INF/MANIFEST.MF attributes).
 * Eliminates the need for external databases or config files to determine server type and version.
 */
public class ServerJarMetadata {

    public static final String MANIFEST_ATTR_SERVER_TYPE = "Murces-Server-Type";
    public static final String MANIFEST_ATTR_GAME_VERSION = "Murces-Game-Version";
    public static final String MANIFEST_ATTR_LOADER_VERSION = "Murces-Loader-Version";
    public static final String MANIFEST_ATTR_INSTALLED_AT = "Murces-Installed-At";
    public static final String META_JSON_ENTRY = "META-INF/murces-server.json";

    private final String serverType;
    private final String gameVersion;
    private final String loaderVersion;
    private final long installedAt;
    private final String jarFilename;

    public ServerJarMetadata(String serverType, String gameVersion, String loaderVersion, long installedAt, String jarFilename) {
        this.serverType = serverType != null ? serverType.toLowerCase().trim() : "unknown";
        this.gameVersion = gameVersion != null ? gameVersion.trim() : "unknown";
        this.loaderVersion = loaderVersion != null ? loaderVersion.trim() : "";
        this.installedAt = installedAt > 0 ? installedAt : System.currentTimeMillis();
        this.jarFilename = jarFilename != null ? jarFilename : "server.jar";
    }

    public String getServerType() {
        return serverType;
    }

    public String getGameVersion() {
        return gameVersion;
    }

    public String getLoaderVersion() {
        return loaderVersion;
    }

    public long getInstalledAt() {
        return installedAt;
    }

    public String getJarFilename() {
        return jarFilename;
    }

    public String getFormattedTitle() {
        String typeFormatted = Character.toUpperCase(serverType.charAt(0)) + serverType.substring(1);
        if (!loaderVersion.isEmpty() && !"latest".equalsIgnoreCase(loaderVersion)) {
            return typeFormatted + " " + gameVersion + " (" + loaderVersion + ")";
        }
        return typeFormatted + " " + gameVersion;
    }

    @Override
    public String toString() {
        return "ServerJarMetadata{" +
                "serverType='" + serverType + '\'' +
                ", gameVersion='" + gameVersion + '\'' +
                ", loaderVersion='" + loaderVersion + '\'' +
                ", installedAt=" + installedAt +
                ", jarFilename='" + jarFilename + '\'' +
                '}';
    }

    /**
     * Records standardized server metadata directly into the server JAR file (both in META-INF/murces-server.json
     * and META-INF/MANIFEST.MF attributes) AND as a standalone .murces-server.json file in serverDir.
     */
    public static boolean recordServerMetadata(File serverDir, String serverType, String gameVersion, String loaderVersion) {
        if (serverDir == null) serverDir = new File(".");
        boolean success = false;
        long now = System.currentTimeMillis();

        // 1. Always write standalone .murces-server.json and murces-server.json in serverDir
        try {
            JsonObject json = new JsonObject();
            json.addProperty("serverType", serverType != null ? serverType.toLowerCase().trim() : "unknown");
            json.addProperty("gameVersion", gameVersion != null ? gameVersion.trim() : "unknown");
            json.addProperty("loaderVersion", loaderVersion != null ? loaderVersion.trim() : "");
            json.addProperty("installedAt", now);
            String jsonContent = json.toString();

            Files.writeString(new File(serverDir, ".murces-server.json").toPath(), jsonContent, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            Files.writeString(new File(serverDir, "murces-server.json").toPath(), jsonContent, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            success = true;
        } catch (Exception ignored) {}

        // 2. Also record into primary JAR if available
        File targetJar = findPrimaryServerJar(serverDir, serverType);
        if (targetJar != null && targetJar.exists() && targetJar.length() > 0) {
            if (writeMetadataToJar(targetJar, serverType, gameVersion, loaderVersion)) {
                success = true;
            }
        }

        // Also record to server.jar if it exists and is different from targetJar (e.g. Fabric server.jar alongside fabric-server-launch.jar)
        File sJar = new File(serverDir, "server.jar");
        if (sJar.exists() && !sJar.equals(targetJar) && sJar.length() > 0) {
            writeMetadataToJar(sJar, serverType, gameVersion, loaderVersion);
        }

        return success;
    }

    private static boolean writeMetadataToJar(File targetJar, String serverType, String gameVersion, String loaderVersion) {
        if (targetJar == null || !targetJar.exists() || targetJar.length() == 0) return false;
        try {
            long now = System.currentTimeMillis();
            JsonObject json = new JsonObject();
            json.addProperty("serverType", serverType != null ? serverType.toLowerCase().trim() : "unknown");
            json.addProperty("gameVersion", gameVersion != null ? gameVersion.trim() : "unknown");
            json.addProperty("loaderVersion", loaderVersion != null ? loaderVersion.trim() : "");
            json.addProperty("installedAt", now);
            String jsonContent = json.toString();

            Path jarPath = targetJar.toPath();
            Map<String, String> env = new HashMap<>();
            env.put("create", "false");

            URI jarUri = URI.create("jar:" + jarPath.toUri());
            try (FileSystem zipfs = FileSystems.newFileSystem(jarUri, env)) {
                Path metaInf = zipfs.getPath("META-INF");
                if (!Files.exists(metaInf)) {
                    Files.createDirectories(metaInf);
                }

                // 1. Write META-INF/murces-server.json
                Path jsonPath = zipfs.getPath(META_JSON_ENTRY);
                Files.writeString(jsonPath, jsonContent, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

                // 2. Write META-INF/MANIFEST.MF
                Path manifestPath = zipfs.getPath("META-INF", "MANIFEST.MF");
                Manifest manifest = new Manifest();
                if (Files.exists(manifestPath)) {
                    try (InputStream in = Files.newInputStream(manifestPath)) {
                        manifest.read(in);
                    } catch (Exception ignored) {}
                }
                Attributes attr = manifest.getMainAttributes();
                if (!attr.containsKey(Attributes.Name.MANIFEST_VERSION)) {
                    attr.put(Attributes.Name.MANIFEST_VERSION, "1.0");
                }
                attr.putValue(MANIFEST_ATTR_SERVER_TYPE, serverType != null ? serverType.toLowerCase().trim() : "unknown");
                attr.putValue(MANIFEST_ATTR_GAME_VERSION, gameVersion != null ? gameVersion.trim() : "unknown");
                if (loaderVersion != null && !loaderVersion.isEmpty()) {
                    attr.putValue(MANIFEST_ATTR_LOADER_VERSION, loaderVersion.trim());
                }
                attr.putValue(MANIFEST_ATTR_INSTALLED_AT, String.valueOf(now));

                try (OutputStream out = Files.newOutputStream(manifestPath,
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                    manifest.write(out);
                }
                return true;
            }
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Reads the standardized server metadata directly from the server JAR file or standalone metadata file.
     */
    public static ServerJarMetadata readServerMetadata(File serverDir) {
        if (serverDir == null) serverDir = new File(".");

        // 1. Check for standalone .murces-server.json or murces-server.json in serverDir
        File metaFile = new File(serverDir, ".murces-server.json");
        if (!metaFile.exists() || metaFile.length() == 0) {
            metaFile = new File(serverDir, "murces-server.json");
        }
        if (metaFile.exists() && metaFile.length() > 0) {
            try {
                String raw = Files.readString(metaFile.toPath(), StandardCharsets.UTF_8);
                JsonObject obj = JsonParser.parseString(raw).getAsJsonObject();
                String sType = obj.has("serverType") ? obj.get("serverType").getAsString() : "unknown";
                String gVer = obj.has("gameVersion") ? obj.get("gameVersion").getAsString() : "unknown";
                String lVer = obj.has("loaderVersion") ? obj.get("loaderVersion").getAsString() : "";
                long instAt = obj.has("installedAt") ? obj.get("installedAt").getAsLong() : metaFile.lastModified();
                return new ServerJarMetadata(sType, gVer, lVer, instAt, metaFile.getName());
            } catch (Exception ignored) {}
        }

        // 2. Check candidate JAR files
        List<File> candidates = findCandidateJars(serverDir);
        for (File jar : candidates) {
            if (!jar.exists() || jar.length() < 50) continue;
            ServerJarMetadata meta = inspectJar(jar);
            if (meta != null) {
                return meta;
            }
        }

        // 3. Heuristic inspection for script-based / modern Forge / NeoForge / script setups
        File runSh = new File(serverDir, "run.sh");
        File runBat = new File(serverDir, "run.bat");
        File userJvmArgs = new File(serverDir, "user_jvm_args.txt");
        File libDir = new File(serverDir, "libraries");

        if (runSh.exists() || runBat.exists() || userJvmArgs.exists() || libDir.exists()) {
            String sType = null;
            String gVer = null;
            String lVer = null;

            File forgeLib = new File(serverDir, "libraries/net/minecraftforge/forge");
            File neoforgeLib = new File(serverDir, "libraries/net/neoforged/neoforge");

            if (forgeLib.exists() && forgeLib.isDirectory()) {
                sType = "forge";
                File[] verDirs = forgeLib.listFiles(File::isDirectory);
                if (verDirs != null && verDirs.length > 0) {
                    String dirName = verDirs[0].getName();
                    int dash = dirName.indexOf('-');
                    if (dash > 0) {
                        gVer = dirName.substring(0, dash);
                        lVer = dirName.substring(dash + 1);
                    } else {
                        gVer = dirName;
                    }
                }
            } else if (neoforgeLib.exists() && neoforgeLib.isDirectory()) {
                sType = "neoforge";
                File[] verDirs = neoforgeLib.listFiles(File::isDirectory);
                if (verDirs != null && verDirs.length > 0) {
                    lVer = verDirs[0].getName();
                    String[] parts = lVer.split("\\.");
                    if (parts.length >= 2) {
                        gVer = "1." + parts[0] + "." + parts[1];
                    }
                }
            }

            if (sType == null && runSh.exists()) {
                try {
                    String scriptContent = Files.readString(runSh.toPath(), StandardCharsets.UTF_8);
                    if (scriptContent.contains("minecraftforge") || scriptContent.contains("forge")) {
                        sType = "forge";
                    } else if (scriptContent.contains("neoforge")) {
                        sType = "neoforge";
                    } else if (scriptContent.contains("fabric")) {
                        sType = "fabric";
                    }
                    java.util.regex.Matcher m = java.util.regex.Pattern.compile("forge/([0-9.]+)-([0-9.]+)").matcher(scriptContent);
                    if (m.find()) {
                        gVer = m.group(1);
                        lVer = m.group(2);
                    }
                } catch (Exception ignored) {}
            }

            if (sType != null) {
                return new ServerJarMetadata(sType, gVer != null ? gVer : "unknown", lVer != null ? lVer : "",
                        runSh.exists() ? runSh.lastModified() : System.currentTimeMillis(),
                        runSh.exists() ? "run.sh" : "libraries");
            }
        }

        return null;
    }

    private static ServerJarMetadata inspectJar(File jar) {
        try (ZipFile zip = new ZipFile(jar)) {
            // 1. Check META-INF/murces-server.json
            ZipEntry jsonEntry = zip.getEntry(META_JSON_ENTRY);
            if (jsonEntry != null) {
                try (InputStream in = zip.getInputStream(jsonEntry)) {
                    String raw = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                    JsonObject obj = JsonParser.parseString(raw).getAsJsonObject();
                    String sType = obj.has("serverType") ? obj.get("serverType").getAsString() : "unknown";
                    String gVer = obj.has("gameVersion") ? obj.get("gameVersion").getAsString() : "unknown";
                    String lVer = obj.has("loaderVersion") ? obj.get("loaderVersion").getAsString() : "";
                    long instAt = obj.has("installedAt") ? obj.get("installedAt").getAsLong() : 0L;
                    return new ServerJarMetadata(sType, gVer, lVer, instAt, jar.getName());
                } catch (Exception ignored) {}
            }

            // 2. Check META-INF/MANIFEST.MF Murces attributes
            ZipEntry manifestEntry = zip.getEntry("META-INF/MANIFEST.MF");
            if (manifestEntry != null) {
                try (InputStream in = zip.getInputStream(manifestEntry)) {
                    Manifest manifest = new Manifest(in);
                    Attributes attr = manifest.getMainAttributes();
                    if (attr.containsKey(new Attributes.Name(MANIFEST_ATTR_SERVER_TYPE))) {
                        String sType = attr.getValue(MANIFEST_ATTR_SERVER_TYPE);
                        String gVer = attr.getValue(MANIFEST_ATTR_GAME_VERSION);
                        String lVer = attr.getValue(MANIFEST_ATTR_LOADER_VERSION);
                        String instAtStr = attr.getValue(MANIFEST_ATTR_INSTALLED_AT);
                        long instAt = 0L;
                        try {
                            if (instAtStr != null) instAt = Long.parseLong(instAtStr);
                        } catch (Exception ignored) {}
                        return new ServerJarMetadata(sType, gVer, lVer, instAt, jar.getName());
                    }

                    // 3. Fallback heuristic detection for pre-existing / externally installed servers
                    String implTitle = attr.getValue("Implementation-Title");
                    String implVersion = attr.getValue("Implementation-Version");
                    String specVersion = attr.getValue("Specification-Version");
                    String mainClass = attr.getValue("Main-Class");
                    String fabricLoader = attr.getValue("Fabric-Loader-Version");
                    String classPath = attr.getValue("Class-Path");

                    String sType = null;
                    String gVer = null;
                    String lVer = null;

                    if ("fabric-server-launch.jar".equalsIgnoreCase(jar.getName()) || fabricLoader != null
                            || (mainClass != null && mainClass.contains("fabricmc"))) {
                        sType = "fabric";
                        if (fabricLoader != null) {
                            lVer = fabricLoader;
                        }
                        if (classPath != null) {
                            java.util.regex.Matcher m = java.util.regex.Pattern.compile("intermediary/([0-9.]+)/").matcher(classPath);
                            if (m.find()) {
                                gVer = m.group(1);
                            }
                            java.util.regex.Matcher ml = java.util.regex.Pattern.compile("fabric-loader/([0-9.]+)/").matcher(classPath);
                            if (ml.find() && (lVer == null || lVer.isEmpty())) {
                                lVer = ml.group(1);
                            }
                        }
                        // Also check sibling server.jar if game version not yet discovered
                        if (gVer == null && jar.getParentFile() != null) {
                            File siblingServerJar = new File(jar.getParentFile(), "server.jar");
                            if (siblingServerJar.exists()) {
                                try (ZipFile sZip = new ZipFile(siblingServerJar)) {
                                    ZipEntry sVerEntry = sZip.getEntry("version.json");
                                    if (sVerEntry != null) {
                                        try (InputStream svin = sZip.getInputStream(sVerEntry)) {
                                            String svRaw = new String(svin.readAllBytes(), StandardCharsets.UTF_8);
                                            JsonObject svObj = JsonParser.parseString(svRaw).getAsJsonObject();
                                            if (svObj.has("id")) {
                                                gVer = svObj.get("id").getAsString();
                                            } else if (svObj.has("name")) {
                                                gVer = svObj.get("name").getAsString();
                                            }
                                        }
                                    }
                                } catch (Exception ignored) {}
                            }
                        }
                    } else if (implTitle != null && implTitle.toLowerCase().contains("paper")) {
                        sType = "paper";
                    } else if (implTitle != null && (implTitle.toLowerCase().contains("spigot") || implTitle.toLowerCase().contains("craftbukkit"))) {
                        sType = "spigot";
                    } else if (implTitle != null && implTitle.toLowerCase().contains("forge")) {
                        sType = "forge";
                    } else if (implTitle != null && implTitle.toLowerCase().contains("neoforge")) {
                        sType = "neoforge";
                    }

                    // Attempt to extract game version from version.json inside Minecraft server jars
                    ZipEntry verJsonEntry = zip.getEntry("version.json");
                    if (verJsonEntry != null && gVer == null) {
                        try (InputStream vin = zip.getInputStream(verJsonEntry)) {
                            String vRaw = new String(vin.readAllBytes(), StandardCharsets.UTF_8);
                            JsonObject vObj = JsonParser.parseString(vRaw).getAsJsonObject();
                            if (vObj.has("id")) {
                                gVer = vObj.get("id").getAsString();
                            } else if (vObj.has("name")) {
                                gVer = vObj.get("name").getAsString();
                            }
                        } catch (Exception ignored) {}
                    }

                    if (gVer == null && specVersion != null && specVersion.matches("\\d+\\.\\d+(\\.\\d+)?")) {
                        gVer = specVersion;
                    }
                    if (gVer == null && implVersion != null) {
                        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+\\.\\d+(\\.\\d+)?)").matcher(implVersion);
                        if (m.find()) {
                            gVer = m.group(1);
                        }
                    }

                    if (sType == null && jar.getName().equals("server.jar")) {
                        if (zip.getEntry("net/minecraft/server/MinecraftServer.class") != null
                                || zip.getEntry("version.json") != null
                                || zip.getEntry("META-INF/versions") != null
                                || (mainClass != null && mainClass.contains("bundler"))) {
                            sType = "vanilla";
                        }
                    }

                    if (sType != null) {
                        return new ServerJarMetadata(sType, gVer != null ? gVer : "unknown", lVer != null ? lVer : "", jar.lastModified(), jar.getName());
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static File findPrimaryServerJar(File serverDir, String serverType) {
        if ("fabric".equalsIgnoreCase(serverType)) {
            File fab = new File(serverDir, "fabric-server-launch.jar");
            if (fab.exists()) return fab;
        }
        File sJar = new File(serverDir, "server.jar");
        if (sJar.exists()) return sJar;
        File fab = new File(serverDir, "fabric-server-launch.jar");
        if (fab.exists()) return fab;

        File[] jars = serverDir.listFiles((dir, name) -> name.endsWith(".jar") && !name.endsWith(".tmp"));
        if (jars != null && jars.length > 0) {
            Arrays.sort(jars, (a, b) -> Long.compare(b.length(), a.length()));
            return jars[0];
        }
        return sJar;
    }

    private static List<File> findCandidateJars(File serverDir) {
        List<File> list = new ArrayList<>();
        File fab = new File(serverDir, "fabric-server-launch.jar");
        if (fab.exists()) list.add(fab);
        File sJar = new File(serverDir, "server.jar");
        if (sJar.exists()) list.add(sJar);

        File[] jars = serverDir.listFiles((dir, name) -> name.endsWith(".jar")
                && !name.endsWith(".tmp")
                && !name.equals("fabric-server-launch.jar")
                && !name.equals("server.jar")
                && !name.equals("BuildTools.jar")
                && !name.equals("fabric-installer.jar")
                && !name.endsWith("-installer.jar"));
        if (jars != null) {
            Arrays.sort(jars, (a, b) -> Long.compare(b.length(), a.length()));
            Collections.addAll(list, jars);
        }
        return list;
    }

    /**
     * Checks if a target mod or modpack (by loader and game version) is compatible with the installed server.
     */
    public static CompatibilityResult checkCompatibility(ServerJarMetadata installed, String targetLoader, String targetGameVersion) {
        if (installed == null) {
            return CompatibilityResult.rejected(
                    "No Minecraft server is installed. You must install a server engine first from Install Server [I] before installing mods or modpacks."
            );
        }

        String sType = installed.getServerType().toLowerCase();
        String sVer = installed.getGameVersion();

        // 1. Vanilla Server Check
        if ("vanilla".equals(sType)) {
            return CompatibilityResult.rejected(
                    "Installed server is Vanilla Minecraft (" + sVer + "). Vanilla servers do not support mods or modpacks. Please install Fabric, Forge, or NeoForge [I] first."
            );
        }

        // 2. Paper / Spigot Server Check
        if ("paper".equals(sType) || "spigot".equals(sType)) {
            return CompatibilityResult.rejected(
                    "Installed server is " + installed.getFormattedTitle() + ". Paper/Spigot only supports Bukkit/Spigot plugins, not Fabric/Forge mods. Please install Fabric, Forge, or NeoForge [I] first."
            );
        }

        // 3. Loader Compatibility Check
        if (targetLoader != null && !targetLoader.isEmpty() && !"any".equalsIgnoreCase(targetLoader) && !"all".equalsIgnoreCase(targetLoader)) {
            String tLoader = targetLoader.toLowerCase().trim();
            if ("unknown".equals(sType)) {
                // If server type couldn't be definitively determined from files, allow
            } else if ("fabric".equals(sType) || "quilt".equals(sType)) {
                if (!"fabric".equals(tLoader) && !"quilt".equals(tLoader)) {
                    return CompatibilityResult.rejected(
                            "Incompatible mod loader: Mod/Modpack requires " + targetLoader.toUpperCase() + ", but the installed server is " + installed.getFormattedTitle() + "."
                    );
                }
            } else if ("forge".equals(sType)) {
                if (!"forge".equals(tLoader)) {
                    return CompatibilityResult.rejected(
                            "Incompatible mod loader: Mod/Modpack requires " + targetLoader.toUpperCase() + ", but the installed server is " + installed.getFormattedTitle() + "."
                    );
                }
            } else if ("neoforge".equals(sType)) {
                if (!"neoforge".equals(tLoader) && !"forge".equals(tLoader)) {
                    return CompatibilityResult.rejected(
                            "Incompatible mod loader: Mod/Modpack requires " + targetLoader.toUpperCase() + ", but the installed server is " + installed.getFormattedTitle() + "."
                    );
                }
            }
        }

        // 4. Game Version Compatibility Check
        if (targetGameVersion != null && !targetGameVersion.isEmpty() && !"any".equalsIgnoreCase(targetGameVersion) && !"all".equalsIgnoreCase(targetGameVersion)) {
            String tVer = targetGameVersion.trim();
            if (!"unknown".equalsIgnoreCase(sVer) && !sVer.isEmpty() && !sVer.equalsIgnoreCase(tVer)) {
                return CompatibilityResult.rejected(
                        "Incompatible game version: Mod/Modpack is designed for Minecraft " + tVer + ", but the installed server is Minecraft " + sVer + "."
                    );
            }
        }

        return CompatibilityResult.accepted();
    }

    /**
     * Checks if a plugin is compatible with the installed server.
     */
    public static CompatibilityResult checkPluginCompatibility(ServerJarMetadata installed, String targetPlatform, String targetGameVersion) {
        if (installed == null) {
            return CompatibilityResult.rejected(
                    "No Minecraft server is installed. You must install a Paper or Spigot server engine first from [I] Install Server Engine before installing plugins."
            );
        }

        String sType = installed.getServerType().toLowerCase();
        String sVer = installed.getGameVersion();

        // 1. Vanilla Server Check
        if ("vanilla".equals(sType)) {
            return CompatibilityResult.rejected(
                    "Installed server is Vanilla Minecraft (" + sVer + "). Vanilla servers do not support plugins. Please install Paper or Spigot [I] first."
            );
        }

        // 2. Fabric / Forge / NeoForge Server Check
        if ("fabric".equals(sType) || "quilt".equals(sType) || "forge".equals(sType) || "neoforge".equals(sType)) {
            return CompatibilityResult.rejected(
                    "Installed server is " + installed.getFormattedTitle() + ". Fabric/Forge servers use mods (in mods/), not Paper/Spigot plugins. Please install Paper or Spigot [I] to run plugins."
            );
        }

        // 3. Paper / Spigot
        if (!"paper".equals(sType) && !"spigot".equals(sType)) {
            return CompatibilityResult.rejected(
                    "Installed server is " + installed.getFormattedTitle() + " which does not support Bukkit/Paper plugins. Please install Paper or Spigot [I]."
            );
        }

        return CompatibilityResult.accepted();
    }

    public static class CompatibilityResult {
        private final boolean compatible;
        private final String message;

        private CompatibilityResult(boolean compatible, String message) {
            this.compatible = compatible;
            this.message = message;
        }

        public static CompatibilityResult accepted() {
            return new CompatibilityResult(true, "Compatible");
        }

        public static CompatibilityResult rejected(String reason) {
            return new CompatibilityResult(false, reason);
        }

        public boolean isCompatible() {
            return compatible;
        }

        public String getMessage() {
            return message;
        }
    }
}
