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
                System.out.println("murces v0.1 (native-image compatibility build)");
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

            MultiWindowTextGUI gui = new MultiWindowTextGUI(screen);
            gui.setTheme(new MinecraftTheme());

            System.out.println("[TEST] Instantiating ServerControlWindow...");
            ServerControlWindow scw = new ServerControlWindow(gui);
            gui.addWindow(scw);
            gui.updateScreen();
            gui.removeWindow(scw);

            System.out.println("[TEST] Instantiating InstallServerWindow...");
            InstallServerWindow isw = new InstallServerWindow(gui);
            gui.addWindow(isw);
            gui.updateScreen();
            gui.removeWindow(isw);

            System.out.println("[TEST] Instantiating ConfigServerWindow...");
            ConfigServerWindow csw = new ConfigServerWindow(gui);
            gui.addWindow(csw);
            gui.updateScreen();
            gui.removeWindow(csw);

            System.out.println("[TEST] Instantiating BackupWindow...");
            BackupWindow bw = new BackupWindow(gui);
            gui.addWindow(bw);
            gui.updateScreen();
            gui.removeWindow(bw);

            System.out.println("[TEST] Instantiating MigratePlayerWindow...");
            MigratePlayerWindow mpw = new MigratePlayerWindow(gui);
            gui.addWindow(mpw);
            gui.updateScreen();
            gui.removeWindow(mpw);

            System.out.println("[TEST] Instantiating ModBrowseWindow...");
            ModBrowseWindow mbw = new ModBrowseWindow(gui);
            gui.addWindow(mbw);
            gui.updateScreen();
            gui.removeWindow(mbw);

            System.out.println("[TEST] Instantiating ModManageWindow...");
            ModManageWindow mmw = new ModManageWindow(gui);
            gui.addWindow(mmw);
            gui.updateScreen();
            gui.removeWindow(mmw);

            System.out.println("[TEST] Instantiating MainMenuWindow...");
            MainMenuWindow mm = new MainMenuWindow(gui);
            gui.addWindow(mm);
            gui.updateScreen();
            gui.removeWindow(mm);

            screen.stopScreen();
            System.out.println("[TEST] All 7 TUI modules instantiated, rendered, and verified successfully!");
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

        VirtualScreen virtualScreen = null;
        MultiWindowTextGUI gui = null;
        try {
            Screen baseScreen = new TerminalScreen(terminal);
            virtualScreen = new VirtualScreen(baseScreen);
            virtualScreen.setMinimumSize(new TerminalSize(68, 18));
            virtualScreen.startScreen();

            gui = new MultiWindowTextGUI(virtualScreen);
            gui.setBlockingIO(false);
            gui.setTheme(new MinecraftTheme());

            TerminalResizeHelper.setup(virtualScreen, gui, terminal);

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                OrchestratorBridge.getInstance().stopOrchestrator();
            }));

            gui.addWindowAndWait(new MainMenuWindow(gui));

        } catch (IOException e) {
            System.err.println("Error initializing terminal GUI: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (gui != null && gui.getGUIThread() instanceof com.googlecode.lanterna.gui2.AsynchronousTextGUIThread) {
                try {
                    ((com.googlecode.lanterna.gui2.AsynchronousTextGUIThread) gui.getGUIThread()).stop();
                } catch (Exception ignored) {}
            }
            if (virtualScreen != null) {
                try {
                    virtualScreen.stopScreen();
                } catch (IOException ignored) {}
            }
            OrchestratorBridge.getInstance().stopOrchestrator();
        }
    }
}
