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
                System.out.println("  murces              Launch interactive Minecraft TUI");
                System.out.println("  murces start [-p]   Start server (--public for Playit tunnel)");
                System.out.println("  murces stop         Stop server");
                System.out.println("  murces status       Check server running status");
                System.out.println("  murces backup       Run world backup");
                System.out.println("  murces --test-tui   Run automated self-test of all TUI windows");
                System.out.println("  murces --version    Show version");
                break;
            case "--version":
            case "-v":
                System.out.println("murces v1.0.0 (production release)");
                break;
            case "--test-tui":
                runSelfTest();
                break;
            case "status":
                boolean running = OrchestratorBridge.isServerRunning();
                System.out.println("Server status: " + (running ? "RUNNING" : "STOPPED"));
                break;
            case "start":
                boolean pub = args.length > 1 && ("-p".equals(args[1]) || "--public".equals(args[1]));
                OrchestratorBridge.ProcessResult startRes = OrchestratorBridge.startServer(pub);
                System.out.println(startRes.output);
                break;
            case "stop":
                OrchestratorBridge.ProcessResult stopRes = OrchestratorBridge.stopServer();
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
