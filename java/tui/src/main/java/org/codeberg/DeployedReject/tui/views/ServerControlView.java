package org.codeberg.DeployedReject.tui.views;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.*;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import org.codeberg.DeployedReject.tui.backend.OrchestratorBridge;
import org.codeberg.DeployedReject.tui.theme.MinecraftTheme;

import java.util.HashMap;
import java.util.Map;

public class ServerControlView implements WorkspaceView {

    private final MainWindow mainWindow;
    private final Panel root;
    private final Label statusLabel;
    private final CheckBox publicTunnelCheckBox;
    private final TextBox commandInput;
    private final Button startBtn;
    private final Button stopBtn;
    private final Button restartBtn;
    private final Button refreshBtn;
    private final Button sendBtn;
    private final Button backBtn;
    private final Map<Character, Runnable> hotkeys = new HashMap<>();

    public ServerControlView(MainWindow mainWindow) {
        this.mainWindow = mainWindow;
        this.root = new Panel(new LinearLayout(Direction.VERTICAL));

        // 1. Status Section
        Panel statusPanel = new Panel(new LinearLayout(Direction.HORIZONTAL));
        statusPanel.addComponent(new Label("Server Status: "));
        statusLabel = new Label("CHECKING...");
        statusPanel.addComponent(statusLabel);

        refreshBtn = new Button("[U]pdate Status", this::updateStatus);
        statusPanel.addComponent(new EmptySpace(new TerminalSize(1, 1)));
        statusPanel.addComponent(refreshBtn);
        root.addComponent(statusPanel.withBorder(Borders.singleLine("Status")));

        // 2. Playit Tunnel Checkbox
        publicTunnelCheckBox = new CheckBox("[P]layit Tunnel (--public)");
        root.addComponent(publicTunnelCheckBox);

        // 3. Actions
        Panel actionPanel = new Panel(new LinearLayout(Direction.HORIZONTAL));
        startBtn = new Button("[S]tart Server", this::onStart);
        stopBtn = new Button("[T]erminate Server", this::onStop);
        restartBtn = new Button("[R]estart Server", this::onRestart);

        actionPanel.addComponent(startBtn);
        actionPanel.addComponent(new EmptySpace(new TerminalSize(1, 1)));
        actionPanel.addComponent(stopBtn);
        actionPanel.addComponent(new EmptySpace(new TerminalSize(1, 1)));
        actionPanel.addComponent(restartBtn);
        root.addComponent(actionPanel.withBorder(Borders.singleLine("Actions")));

        // 4. Console Command
        Panel cmdPanel = new Panel(new LinearLayout(Direction.HORIZONTAL));
        cmdPanel.addComponent(new Label("[C]onsole Cmd (/): "));
        commandInput = new TextBox(new TerminalSize(22, 1));
        sendBtn = new Button("[D]ispatch", this::onSendCommand);
        commandInput.setInputFilter((interactable, keyStroke) -> {
            if (keyStroke.getKeyType() == KeyType.Escape || keyStroke.getKeyType() == KeyType.ArrowDown) {
                sendBtn.takeFocus();
                return false;
            }
            return true;
        });
        cmdPanel.addComponent(commandInput);
        cmdPanel.addComponent(new EmptySpace(new TerminalSize(1, 1)));
        cmdPanel.addComponent(sendBtn);
        root.addComponent(cmdPanel.withBorder(Borders.singleLine("Command Dispatch")));

        // 5. Navigation Footer
        backBtn = new Button("[B]ack to Main Menu", mainWindow::showMainMenu);
        root.addComponent(backBtn);

        // Configure Hotkeys
        hotkeys.put('S', KeyboardNavigationHelper.focus(startBtn, this::onStart));
        hotkeys.put('T', KeyboardNavigationHelper.focus(stopBtn, this::onStop));
        hotkeys.put('R', KeyboardNavigationHelper.focus(restartBtn, this::onRestart));
        hotkeys.put('U', KeyboardNavigationHelper.focus(refreshBtn, this::updateStatus));
        hotkeys.put('P', KeyboardNavigationHelper.focus(publicTunnelCheckBox, () -> publicTunnelCheckBox.setChecked(!publicTunnelCheckBox.isChecked())));
        hotkeys.put('D', KeyboardNavigationHelper.focus(sendBtn, this::onSendCommand));
        hotkeys.put('B', KeyboardNavigationHelper.focus(backBtn, mainWindow::showMainMenu));
        hotkeys.put('/', commandInput::takeFocus);
        hotkeys.put('C', commandInput::takeFocus);
    }

    @Override
    public String getTitle() {
        return "Server Control";
    }

    @Override
    public Component getComponent() {
        return root;
    }

    @Override
    public Map<Character, Runnable> getHotkeys() {
        return hotkeys;
    }

    @Override
    public Interactable getDefaultFocus() {
        return startBtn;
    }

    @Override
    public void onResized(TerminalSize newSize) {
        if (newSize == null) return;
        int cols = newSize.getColumns();
        int actWidth = Math.max(28, Math.min(65, (cols * 35) / 100));
        int wsWidth = Math.max(44, cols - actWidth - 6);

        int cmdWidth = Math.max(22, Math.min(50, wsWidth - 28));
        commandInput.setPreferredSize(new TerminalSize(cmdWidth, 1));
    }

    @Override
    public void onActivated() {
        updateStatus();
    }

    public void updateStatus() {
        new Thread(() -> {
            boolean downloading = OrchestratorBridge.isServerDownloading();
            boolean running = OrchestratorBridge.isServerRunning();
            boolean installed = OrchestratorBridge.isServerInstalled();
            mainWindow.getGui().getGUIThread().invokeLater(() -> {
                if (downloading) {
                    statusLabel.setText("[INSTALLING...]");
                    statusLabel.setForegroundColor(MinecraftTheme.GOLD_YELLOW);
                } else if (running) {
                    statusLabel.setText("[RUNNING] - Port 25565");
                    statusLabel.setForegroundColor(MinecraftTheme.CREEPER_GREEN);
                } else if (!installed) {
                    statusLabel.setText("[NOT INSTALLED]");
                    statusLabel.setForegroundColor(MinecraftTheme.STONE_GRAY);
                } else {
                    statusLabel.setText("[STOPPED]");
                    statusLabel.setForegroundColor(MinecraftTheme.REDSTONE_RED);
                }
            });
        }).start();
    }

    private void onStart() {
        if (OrchestratorBridge.isServerDownloading()) {
            ActivityLogger.warn("Cannot start server: installation or download is currently in progress. Please wait for it to complete.");
            updateStatus();
            return;
        }
        if (!OrchestratorBridge.isServerInstalled()) {
            ActivityLogger.warn("No complete Minecraft server is installed yet! Please select '[I]nstall Server Engine' first.");
            updateStatus();
            return;
        }
        if (OrchestratorBridge.isServerRunning()) {
            ActivityLogger.warn("Server is already running.");
            return;
        }

        boolean pub = publicTunnelCheckBox.isChecked();
        statusLabel.setText("[STARTING...]");
        statusLabel.setForegroundColor(MinecraftTheme.GOLD_YELLOW);
        ActivityLogger.info("Starting server (public=" + pub + ")...");

        new Thread(() -> {
            OrchestratorBridge.ProcessResult res = OrchestratorBridge.startServer(pub);
            mainWindow.getGui().getGUIThread().invokeLater(() -> {
                if (res.exitCode == 0) {
                    ActivityLogger.ok(res.output.isEmpty() ? "Server started successfully." : res.output);
                } else {
                    ActivityLogger.err(res.output.isEmpty() ? "Failed to start server (code " + res.exitCode + ")" : res.output);
                }
                updateStatus();
            });
        }).start();
    }

    private void onStop() {
        if (!OrchestratorBridge.isServerRunning()) {
            ActivityLogger.warn("Server is not running.");
            return;
        }

        statusLabel.setText("[STOPPING...]");
        statusLabel.setForegroundColor(MinecraftTheme.GOLD_YELLOW);
        ActivityLogger.info("Stopping server...");

        new Thread(() -> {
            OrchestratorBridge.ProcessResult res = OrchestratorBridge.stopServer();
            mainWindow.getGui().getGUIThread().invokeLater(() -> {
                if (res.exitCode == 0) {
                    ActivityLogger.ok(res.output.isEmpty() ? "Server stopped." : res.output);
                } else {
                    ActivityLogger.err(res.output.isEmpty() ? "Failed to stop server." : res.output);
                }
                updateStatus();
            });
        }).start();
    }

    private void onRestart() {
        if (OrchestratorBridge.isServerDownloading()) {
            ActivityLogger.warn("Cannot restart server: installation or download is currently in progress.");
            updateStatus();
            return;
        }
        if (!OrchestratorBridge.isServerInstalled()) {
            ActivityLogger.warn("No complete Minecraft server is installed yet! Please select '[I]nstall Server Engine' first.");
            updateStatus();
            return;
        }

        statusLabel.setText("[RESTARTING...]");
        statusLabel.setForegroundColor(MinecraftTheme.GOLD_YELLOW);
        ActivityLogger.info("Restarting server...");

        new Thread(() -> {
            OrchestratorBridge.stopServer();
            try {
                Thread.sleep(1500);
            } catch (InterruptedException ignored) {}
            boolean pub = publicTunnelCheckBox.isChecked();
            OrchestratorBridge.ProcessResult res = OrchestratorBridge.startServer(pub);
            mainWindow.getGui().getGUIThread().invokeLater(() -> {
                if (res.exitCode == 0) {
                    ActivityLogger.ok(res.output.isEmpty() ? "Server restarted." : res.output);
                } else {
                    ActivityLogger.err(res.output);
                }
                updateStatus();
            });
        }).start();
    }

    private void onSendCommand() {
        String cmd = commandInput.getText().trim();
        if (cmd.isEmpty()) return;

        if (!OrchestratorBridge.isServerRunning()) {
            ActivityLogger.warn("Cannot send command: Server is not running.");
            return;
        }

        ActivityLogger.info("Console command sent: /" + cmd);
        commandInput.setText("");

        new Thread(() -> {
            OrchestratorBridge.ProcessResult res = OrchestratorBridge.sendConsoleCommand(cmd);
            mainWindow.getGui().getGUIThread().invokeLater(() -> {
                if (res.output != null && !res.output.isEmpty()) {
                    ActivityLogger.log(res.output);
                } else {
                    ActivityLogger.ok("Command dispatched to server.");
                }
            });
        }).start();
    }
}
