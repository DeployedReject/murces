package org.codeberg.DeployedReject.tui;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.MultiWindowTextGUI;
import com.googlecode.lanterna.gui2.SeparateTextGUIThread;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.screen.VirtualScreen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import com.googlecode.lanterna.terminal.Terminal;
import com.googlecode.lanterna.terminal.virtual.DefaultVirtualTerminal;
import org.codeberg.DeployedReject.tui.backend.OrchestratorBridge;
import org.codeberg.DeployedReject.tui.config.ConfigManager;
import org.codeberg.DeployedReject.tui.config.TuiConfig;
import org.codeberg.DeployedReject.tui.theme.Themes;
import org.codeberg.DeployedReject.tui.theme.MinecraftTheme;
import org.codeberg.DeployedReject.tui.views.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class Main {

    public static void main(String[] args) {
        if (args.length > 0) {
            handleCli(args);
            return;
        }

        launchTui();
    }

    private static void handleCli(String[] args) {
        String cmd = args[0];
        switch (cmd) {
            case "--help":
            case "-h":
                System.out.println("Murces - Minecraft Server Manager TUI");
                System.out.println("Usage:");
                System.out.println("  murces                             Launch interactive Minecraft TUI");
                System.out.println("  murces start [-p] [--session <s>]  Start server (-p for tunnel, custom tmux session)");
                System.out.println("  murces stop [--session <s>]        Stop server");
                System.out.println("  murces status [--session <s>]      Check server running status");
                System.out.println("  murces server-name [name]          View or set the server name and tmux session");
                System.out.println("  murces tunnel <setup|status|reset> Playit.gg tunnel native lifecycle management");
                System.out.println("  murces server-port <port>          Update server.properties port");
                System.out.println("  murces servers                     List configured server profiles");
                System.out.println("  murces switch <profile>            Switch active server profile");
                System.out.println("  murces backup                      Run world backup");
                System.out.println("  murces search-modpacks <query>     Search modpacks on Modrinth");
                System.out.println("  murces install-modpack <slug>      Download & install modpack with dependencies");
                System.out.println("  murces update-mods                 Update all installed mods to latest versions");
                System.out.println("  murces install-jdk [version]       Download portable OpenJDK (8/17/21)");
                System.out.println("  murces --test-tui                  Run automated self-test of all TUI windows");
                System.out.println("  murces --version                   Show version");
                break;
            case "--version":
            case "-v":
                System.out.println("murces v1.6.0 (production release)");
                break;
            case "server-name":
            case "rename":
                if (args.length < 2) {
                    System.out.println("Current server name / tmux session: " + ConfigManager.getInstance().getConfig().getServerName());
                    System.out.println("Usage: murces server-name <new-name>");
                    return;
                }
                String newSrvName = args[1];
                ConfigManager.getInstance().getConfig().setServerName(newSrvName);
                ConfigManager.getInstance().saveConfig();
                System.out.println("Server name and tmux session updated to: " + ConfigManager.getInstance().getConfig().getServerName());
                break;
            case "search-modpacks":
                if (args.length < 2) {
                    System.err.println("Usage: murces search-modpacks <query> [version] [loader]");
                    System.exit(1);
                }
                String smQuery = args[1];
                String smVer = args.length > 2 ? args[2] : ConfigManager.getInstance().getConfig().getGameVersion();
                String smLdr = args.length > 3 ? args[3] : ConfigManager.getInstance().getConfig().getLoader();
                System.out.println("Searching modpacks for '" + smQuery + "' (" + smVer + " / " + smLdr + ")...");
                try {
                    java.util.List<OrchestratorBridge.ModpackResult> smRes = OrchestratorBridge.getInstance().searchModpacks("modrinth", smQuery, smVer, smLdr).get();
                    if (smRes.isEmpty()) {
                        System.out.println("No modpacks found.");
                    } else {
                        System.out.println("Found " + smRes.size() + " modpacks:");
                        for (OrchestratorBridge.ModpackResult mp : smRes) {
                            System.out.println("  - " + mp.name + " (" + mp.slug + ") by " + mp.author + " [" + mp.downloads + " downloads]");
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Search failed: " + e.getMessage());
                }
                break;
            case "install-modpack":
                if (args.length < 2) {
                    System.err.println("Usage: murces install-modpack <slug-or-id> [version] [loader] [--include-client]");
                    System.exit(1);
                }
                String mpSlug = args[1];
                String mpVer = args.length > 2 && !args[2].startsWith("--") ? args[2] : ConfigManager.getInstance().getConfig().getGameVersion();
                String mpLdr = args.length > 3 && !args[3].startsWith("--") ? args[3] : ConfigManager.getInstance().getConfig().getLoader();
                boolean mpIncClient = false;
                for (String a : args) {
                    if ("--include-client".equalsIgnoreCase(a)) mpIncClient = true;
                }

                org.codeberg.DeployedReject.utils.ServerJarMetadata mpMeta = OrchestratorBridge.getInstalledServerMetadata();
                if (mpMeta == null) {
                    System.err.println("[ERROR] No Minecraft server installed. You must install a server engine first before installing modpacks (launch TUI [I]).");
                    System.exit(1);
                }
                var mpCompat = OrchestratorBridge.checkCompatibility(mpLdr, mpVer);
                if (!mpCompat.isCompatible()) {
                    System.err.println("[ERROR] " + mpCompat.getMessage());
                    System.exit(1);
                }

                System.out.println("Resolving modpack '" + mpSlug + "' for MC " + mpVer + " (" + mpLdr + ")...");
                try {
                    java.util.List<OrchestratorBridge.ModpackVersionInfo> vList = OrchestratorBridge.getInstance().getModpackVersions("modrinth", mpSlug, mpVer, mpLdr).get();
                    String vId = !vList.isEmpty() ? vList.get(0).versionId : mpSlug;
                    String mrpackUrl = !vList.isEmpty() ? vList.get(0).mrpackUrl : "";
                    System.out.println("Installing modpack version: " + (!vList.isEmpty() ? vList.get(0).versionName : vId) + " (includeClient=" + mpIncClient + ")...");
                    OrchestratorBridge.ModpackInstallSummary sum = OrchestratorBridge.getInstance().installModpack(
                        "modrinth", mpSlug, vId, mrpackUrl, mpVer, mpLdr, mpIncClient,
                        System.out::println,
                        pct -> {},
                        null
                    ).get();
                    System.out.println(sum.formattedSummary());
                } catch (Exception e) {
                    System.err.println("Failed to install modpack: " + e.getMessage());
                    System.exit(1);
                }
                break;
            case "--test-tui":
                runSelfTest();
                break;
            case "tunnel":
                String tAct = args.length > 1 ? args[1] : "status";
                switch (tAct) {
                    case "setup":
                        org.codeberg.DeployedReject.device.Tunnel.setup();
                        break;
                    case "status":
                        org.codeberg.DeployedReject.device.Tunnel.status();
                        break;
                    case "reset":
                        org.codeberg.DeployedReject.device.Tunnel.reset();
                        break;
                    default:
                        System.err.println("Unknown tunnel action: " + tAct + ". Valid actions: setup, status, reset");
                }
                break;
            case "server-port":
                if (args.length < 2) {
                    System.err.println("Usage: murces server-port <port> [dirOrFile]");
                    System.exit(1);
                }
                try {
                    int pNum = Integer.parseInt(args[1]);
                    String pDir = args.length > 2 ? args[2] : ConfigManager.getInstance().getConfig().getServerDir();
                    org.codeberg.DeployedReject.tui.backend.ServerPropertiesManager.updateServerPort(pDir, pNum);
                    System.out.println("Updated server-port to " + pNum + " in " + pDir);
                } catch (Exception e) {
                    System.err.println("Failed to update server-port: " + e.getMessage());
                    System.exit(1);
                }
                break;
            case "servers":
                TuiConfig cfgSrv = ConfigManager.getInstance().getConfig();
                System.out.println("Active Server Profile: " + cfgSrv.getActiveServer());
                System.out.println("Configured Server Profiles:");
                for (TuiConfig.ServerProfile sp : cfgSrv.getServerProfiles().values()) {
                    System.out.println("  - " + sp.getName() + " [session=" + sp.getSessionName() + ", dir=" + sp.getDirectory() + ", port=" + sp.getPort() + "]");
                }
                break;
            case "switch":
                if (args.length < 2) {
                    System.err.println("Usage: murces switch <profile-name>");
                    System.exit(1);
                }
                TuiConfig cfgSw = ConfigManager.getInstance().getConfig();
                if (cfgSw.getServerProfiles().containsKey(args[1])) {
                    cfgSw.switchServer(args[1]);
                    ConfigManager.getInstance().saveConfig();
                    System.out.println("Switched active server profile to: " + args[1]);
                } else {
                    System.err.println("Server profile '" + args[1] + "' not found.");
                    System.exit(1);
                }
                break;
            case "update-mods":
                String uMc = args.length > 1 ? args[1] : ConfigManager.getInstance().getConfig().getGameVersion();
                String uLd = args.length > 2 ? args[2] : ConfigManager.getInstance().getConfig().getLoader();
                org.codeberg.DeployedReject.utils.ServerJarMetadata uMeta = OrchestratorBridge.getInstalledServerMetadata();
                if (uMeta == null) {
                    System.err.println("[ERROR] No Minecraft server installed. You must install a server engine first before updating mods (launch TUI [I]).");
                    System.exit(1);
                }
                var uCompat = OrchestratorBridge.checkCompatibility(uLd, uMc);
                if (!uCompat.isCompatible()) {
                    System.err.println("[ERROR] " + uCompat.getMessage());
                    System.exit(1);
                }
                System.out.println("Updating all mods for Minecraft " + uMc + " (" + uLd + ")...");
                try {
                    org.codeberg.DeployedReject.tui.backend.ModUpdateManager.UpdateSummary uSum =
                        OrchestratorBridge.getInstance().updateAllMods(uMc, uLd, System.out::println, null).get();
                    System.out.println(uSum.formattedSummary());
                } catch (Exception e) {
                    System.err.println("Error updating mods: " + e.getMessage());
                    System.exit(1);
                }
                break;
            case "install-jdk":
                int jVer = 21;
                if (args.length > 1) {
                    try {
                        jVer = Integer.parseInt(args[1]);
                    } catch (NumberFormatException ignored) {}
                }
                System.out.println("Downloading Portable OpenJDK " + jVer + " from Adoptium...");
                boolean jOk = org.codeberg.DeployedReject.device.JdkManager.downloadAndExtractJdk(jVer, null, System.out::println);
                System.out.println("Portable JDK installation: " + (jOk ? "SUCCESS" : "FAILED"));
                break;
            case "status":
                String stSess = ConfigManager.getInstance().getConfig().getSessionName();
                for (int i = 1; i < args.length; i++) {
                    if ("--session".equals(args[i]) && i + 1 < args.length) {
                        stSess = args[i + 1];
                    }
                }
                boolean running = OrchestratorBridge.isServerRunning(stSess);
                System.out.println("Server status (" + stSess + "): " + (running ? "RUNNING" : "STOPPED"));
                break;
            case "start":
                boolean pub = false;
                String startSess = ConfigManager.getInstance().getConfig().getSessionName();
                String startDir = ConfigManager.getInstance().getConfig().getServerDir();
                for (int i = 1; i < args.length; i++) {
                    if ("-p".equals(args[i]) || "--public".equals(args[i])) {
                        pub = true;
                    } else if ("--session".equals(args[i]) && i + 1 < args.length) {
                        startSess = args[++i];
                    } else if ("--dir".equals(args[i]) && i + 1 < args.length) {
                        startDir = args[++i];
                    }
                }
                OrchestratorBridge.ProcessResult startRes = OrchestratorBridge.startServer(pub, "4G", startSess, startDir);
                System.out.println(startRes.output);
                break;
            case "stop":
                String stopSess = ConfigManager.getInstance().getConfig().getSessionName();
                for (int i = 1; i < args.length; i++) {
                    if ("--session".equals(args[i]) && i + 1 < args.length) {
                        stopSess = args[++i];
                    }
                }
                OrchestratorBridge.ProcessResult stopRes = OrchestratorBridge.stopServer(stopSess);
                System.out.println(stopRes.output);
                break;
            case "backup":
                OrchestratorBridge.ProcessResult bakRes = OrchestratorBridge.runBackup();
                System.out.println(bakRes.output);
                break;
            default:
                System.err.println("Unknown command: " + cmd + ". Run 'murces --help' for usage.");
                System.exit(1);
        }
    }

    public static void runSelfTest() {
        System.out.println("[TEST] Starting Murces TUI self-test...");
        try {
            DefaultVirtualTerminal vt = new DefaultVirtualTerminal(new TerminalSize(80, 25));
            Screen screen = new TerminalScreen(vt);
            screen.startScreen();

            TuiConfig config = ConfigManager.getInstance().getConfig();
            MultiWindowTextGUI gui = new MultiWindowTextGUI(screen);
            gui.setTheme(Themes.createTheme(config.getTheme(), config.getTransparencyPercent(), config.isTrueColor()));

            System.out.println("[TEST] Instantiating MainWindow (Unified 3-Pane Dashboard)...");
            MainWindow mw = new MainWindow(gui);
            gui.addWindow(mw);
            gui.updateScreen();

            System.out.println("[TEST] Testing workspace view swapping...");
            mw.showServerControl();
            gui.updateScreen();
            mw.showInstallServer();
            gui.updateScreen();
            mw.showConfigServer();
            gui.updateScreen();
            mw.showBackup();
            gui.updateScreen();
            mw.showMigratePlayer();
            gui.updateScreen();
            mw.showModBrowse();
            gui.updateScreen();
            mw.showModManage();
            gui.updateScreen();
            mw.showModpackBrowse();
            gui.updateScreen();
            mw.showCustomization();
            gui.updateScreen();
            mw.showJobManager();
            gui.updateScreen();
            mw.showMainMenu();
            gui.updateScreen();

            mw.exit();
            gui.removeWindow(mw);

            screen.stopScreen();
            System.out.println("[TEST] All unified dashboard panes and workspace views instantiated, rendered, and verified successfully!");
        } catch (Exception e) {
            System.err.println("[TEST ERROR] " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void launchTui() {
        ResponsiveTerminal terminal;
        try {
            terminal = new ResponsiveUnixTerminal();
        } catch (Exception ttyEx) {
            terminal = new ResponsiveStreamTerminal(System.in, System.out, StandardCharsets.UTF_8);
        }

        final ResponsiveTerminal termRef = terminal;
        VirtualScreen virtualScreen = null;
        MultiWindowTextGUI gui = null;
        try {
            Screen baseScreen = new TerminalScreen(terminal);
            virtualScreen = new VirtualScreen(baseScreen);
            virtualScreen.setMinimumSize(new TerminalSize(68, 18));
            virtualScreen.startScreen();

            final VirtualScreen vsRef = virtualScreen;

            TuiConfig config = ConfigManager.getInstance().getConfig();
            gui = new MultiWindowTextGUI(virtualScreen);
            gui.setBlockingIO(false);
            gui.setTheme(Themes.createTheme(config.getTheme(), config.getTransparencyPercent(), config.isTrueColor()));

            TerminalResizeHelper.setup(virtualScreen, gui, terminal);

            gui.getGUIThread().setExceptionHandler(new com.googlecode.lanterna.gui2.TextGUIThread.ExceptionHandler() {
                @Override
                public boolean onRuntimeException(RuntimeException e) {
                    ActivityLogger.err("GUI Exception: " + e.getMessage());
                    return true;
                }

                @Override
                public boolean onIOException(IOException e) {
                    return false;
                }
            });

            if (terminal instanceof com.googlecode.lanterna.terminal.ExtendedTerminal) {
                try {
                    ((com.googlecode.lanterna.terminal.ExtendedTerminal) terminal).setMouseCaptureMode(com.googlecode.lanterna.terminal.MouseCaptureMode.CLICK_RELEASE);
                } catch (Exception ignored) {}
            }

            Thread shutdownHook = new Thread(() -> {
                cleanupTerminal(vsRef, termRef);
                OrchestratorBridge.getInstance().stopOrchestrator();
            }, "MurcesShutdown");
            Runtime.getRuntime().addShutdownHook(shutdownHook);

            if (!OrchestratorBridge.isCommandAvailable("tmux")) {
                ActivityLogger.warn("[WARN] 'tmux' is not found in PATH! Background server execution requires tmux.");
                ActivityLogger.warn("[WARN] Install tmux via: sudo apt install tmux (or brew install tmux / pacman -S tmux)");
            }
            if (!OrchestratorBridge.isCommandAvailable("java")) {
                ActivityLogger.warn("[WARN] 'java' is not found in PATH! Minecraft server execution requires Java 17/21+.");
            }

            gui.addWindowAndWait(new MainWindow(gui));

        } catch (IOException e) {
            System.err.println("Error initializing terminal GUI: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (gui != null && gui.getGUIThread() instanceof com.googlecode.lanterna.gui2.AsynchronousTextGUIThread) {
                try {
                    ((com.googlecode.lanterna.gui2.AsynchronousTextGUIThread) gui.getGUIThread()).stop();
                } catch (Exception ignored) {}
            }
            cleanupTerminal(virtualScreen, termRef);
            OrchestratorBridge.getInstance().stopOrchestrator();
            System.exit(0);
        }
    }

    public static void cleanupTerminal(VirtualScreen virtualScreen, Terminal terminal) {
        if (virtualScreen != null) {
            try {
                virtualScreen.stopScreen();
            } catch (Exception ignored) {}
        }
        if (terminal != null) {
            try {
                terminal.close();
            } catch (Exception ignored) {}
        }
        try {
            new ProcessBuilder("stty", "sane").inheritIO().start().waitFor();
        } catch (Exception ignored) {}
        System.out.print("\033[?1000l\033[?1002l\033[?1006l\033[?25h\033[0m");
        System.out.flush();
    }
}
