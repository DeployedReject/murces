package org.codeberg.DeployedReject.tui.views;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.*;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import org.codeberg.DeployedReject.tui.backend.OrchestratorBridge;
import org.codeberg.DeployedReject.tui.theme.GlyphHelper;
import org.codeberg.DeployedReject.tui.theme.MinecraftTheme;

import java.util.HashMap;
import java.util.Map;

public class ServerControlView implements WorkspaceView {

    private final MainWindow mainWindow;
    private final Panel root;
    private final Label statusLabel;
    private final CheckBox publicTunnelCheckBox;
    private final ComboBox<String> ramComboBox;
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
        statusPanel.addComponent(new Label(GlyphHelper.apply(GlyphHelper.ICON_SERVER + " Server Status: ")));
        statusLabel = new Label("CHECKING...");
        statusPanel.addComponent(statusLabel);

        refreshBtn = new Button(GlyphHelper.apply(GlyphHelper.ICON_RESTART + " [U]pdate Status"), this::updateStatus);
        statusPanel.addComponent(new EmptySpace(new TerminalSize(1, 1)));
        statusPanel.addComponent(refreshBtn);
        root.addComponent(statusPanel.withBorder(Borders.singleLine(GlyphHelper.apply(GlyphHelper.ICON_SERVER + " Server State"))));

        // 2. Launch Options (Playit Tunnel & RAM Allocation)
        Panel launchOptionsPanel = new Panel(new LinearLayout(Direction.HORIZONTAL));
        publicTunnelCheckBox = new CheckBox(GlyphHelper.apply(GlyphHelper.ICON_TUNNEL + " [P]layit Tunnel (--public)"));
        launchOptionsPanel.addComponent(publicTunnelCheckBox);
        launchOptionsPanel.addComponent(new EmptySpace(new TerminalSize(2, 1)));

        launchOptionsPanel.addComponent(new Label(GlyphHelper.apply(GlyphHelper.ICON_OPTIONS + " [M]emory (RAM): ")));
        ramComboBox = new ComboBox<>("2G", "4G", "6G", "8G", "12G", "16G", "1G");
        ramComboBox.setPreferredSize(new TerminalSize(8, 1));
        ramComboBox.setSelectedIndex(1); // 4G default
        launchOptionsPanel.addComponent(ramComboBox);

        root.addComponent(launchOptionsPanel.withBorder(Borders.singleLine(GlyphHelper.apply(GlyphHelper.ICON_CONFIG + " Launch Options"))));

        // 3. Actions
        Panel actionPanel = new Panel(new LinearLayout(Direction.HORIZONTAL));
        startBtn = new Button(GlyphHelper.apply(GlyphHelper.ICON_PLAY + " [S]tart Server"), this::onStart);
        stopBtn = new Button(GlyphHelper.apply(GlyphHelper.ICON_STOP + " [T]erminate Server"), this::onStop);
        restartBtn = new Button(GlyphHelper.apply(GlyphHelper.ICON_RESTART + " [R]estart Server"), this::onRestart);

        actionPanel.addComponent(startBtn);
        actionPanel.addComponent(new EmptySpace(new TerminalSize(1, 1)));
        actionPanel.addComponent(stopBtn);
        actionPanel.addComponent(new EmptySpace(new TerminalSize(1, 1)));
        actionPanel.addComponent(restartBtn);
        root.addComponent(actionPanel.withBorder(Borders.singleLine(GlyphHelper.apply(GlyphHelper.ICON_TASKS + " Server Actions"))));

        // Initial sync of button states
        applyButtonStates(OrchestratorBridge.isServerInstalled(), false, false);

        // 4. Console Command
        Panel cmdPanel = new Panel(new LinearLayout(Direction.HORIZONTAL));
        cmdPanel.addComponent(new Label(GlyphHelper.apply(GlyphHelper.ICON_TERMINAL + " [C]onsole Cmd (/): ")));
        commandInput = new TextBox(new TerminalSize(26, 1));
        sendBtn = new Button(GlyphHelper.apply(GlyphHelper.ICON_CHECK + " [D]ispatch"), this::onSendCommand);
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
        root.addComponent(cmdPanel.withBorder(Borders.singleLine(GlyphHelper.apply(GlyphHelper.ICON_TERMINAL + " Command Dispatch"))));

        // 5. Navigation Footer
        backBtn = new Button(GlyphHelper.apply(GlyphHelper.ICON_BACK + " [B]ack to Main Menu"), mainWindow::showMainMenu);
        root.addComponent(backBtn);

        // Configure Hotkeys
        hotkeys.put('S', KeyboardNavigationHelper.focus(startBtn, this::onStart));
        hotkeys.put('T', KeyboardNavigationHelper.focus(stopBtn, this::onStop));
        hotkeys.put('R', KeyboardNavigationHelper.focus(restartBtn, this::onRestart));
        hotkeys.put('U', KeyboardNavigationHelper.focus(refreshBtn, this::updateStatus));
        hotkeys.put('P', KeyboardNavigationHelper.focus(publicTunnelCheckBox, () -> publicTunnelCheckBox.setChecked(!publicTunnelCheckBox.isChecked())));
        hotkeys.put('M', KeyboardNavigationHelper.focus(ramComboBox, this::cycleRam));
        hotkeys.put('A', KeyboardNavigationHelper.focus(ramComboBox, this::cycleRam));
        hotkeys.put('D', KeyboardNavigationHelper.focus(sendBtn, this::onSendCommand));
        hotkeys.put('B', KeyboardNavigationHelper.focus(backBtn, mainWindow::showMainMenu));
        hotkeys.put('/', commandInput::takeFocus);
        hotkeys.put('C', commandInput::takeFocus);
    }

    private void cycleRam() {
        int next = (ramComboBox.getSelectedIndex() + 1) % ramComboBox.getItemCount();
        ramComboBox.setSelectedIndex(next);
    }

    private void applyButtonStates(boolean installed, boolean running, boolean downloading) {
        if (downloading) {
            startBtn.setEnabled(false);
            stopBtn.setEnabled(false);
            restartBtn.setEnabled(false);
        } else if (running) {
            startBtn.setEnabled(false);
            stopBtn.setEnabled(true);
            restartBtn.setEnabled(true);
        } else if (!installed) {
            startBtn.setEnabled(false);
            stopBtn.setEnabled(false);
            restartBtn.setEnabled(false);
        } else { // installed and stopped
            startBtn.setEnabled(true);
            stopBtn.setEnabled(false);
            restartBtn.setEnabled(true);
        }

        if (!startBtn.isEnabled() && mainWindow.getGui() != null && mainWindow.getGui().getFocusedInteractable() == startBtn) {
            Interactable nextFocus = getDefaultFocus();
            if (nextFocus != null) {
                nextFocus.takeFocus();
            }
        }
    }

    @Override
    public String getTitle() {
        return GlyphHelper.apply(GlyphHelper.ICON_SERVER + " Server Control");
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
        if (startBtn.isEnabled()) {
            return startBtn;
        }
        if (stopBtn.isEnabled()) {
            return stopBtn;
        }
        if (restartBtn.isEnabled()) {
            return restartBtn;
        }
        return refreshBtn;
    }

    @Override
    public void onResized(TerminalSize newSize) {
        if (newSize == null) return;
        int cols = newSize.getColumns();
        int actWidth = Math.max(28, Math.min(65, (cols * 35) / 100));
        int wsWidth = Math.max(44, cols - actWidth - 6);

        int cmdWidth = Math.max(26, Math.min(60, wsWidth - 26));
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
                applyButtonStates(installed, running, downloading);
                if (downloading) {
                    statusLabel.setText(GlyphHelper.apply(GlyphHelper.ICON_BUSY + " [INSTALLING...]"));
                    statusLabel.setForegroundColor(MinecraftTheme.GOLD_YELLOW);
                } else if (running) {
                    statusLabel.setText(GlyphHelper.apply("● [RUNNING] - Port 25565"));
                    statusLabel.setForegroundColor(MinecraftTheme.CREEPER_GREEN);
                } else if (!installed) {
                    statusLabel.setText(GlyphHelper.apply("✕ [NOT INSTALLED]"));
                    statusLabel.setForegroundColor(MinecraftTheme.STONE_GRAY);
                } else {
                    statusLabel.setText(GlyphHelper.apply("○ [STOPPED]"));
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
            updateStatus();
            return;
        }

        boolean pub = publicTunnelCheckBox.isChecked();
        String selectedRam = ramComboBox.getSelectedItem();
        statusLabel.setText(GlyphHelper.apply(GlyphHelper.ICON_PLAY + " [STARTING...]"));
        statusLabel.setForegroundColor(MinecraftTheme.GOLD_YELLOW);
        startBtn.setEnabled(false);
        stopBtn.setEnabled(false);
        restartBtn.setEnabled(false);
        ActivityLogger.info("Starting server (public=" + pub + ", RAM=" + selectedRam + ")...");

        new Thread(() -> {
            OrchestratorBridge.ProcessResult res = OrchestratorBridge.startServer(pub, selectedRam);
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
            updateStatus();
            return;
        }

        statusLabel.setText(GlyphHelper.apply(GlyphHelper.ICON_STOP + " [STOPPING...]"));
        statusLabel.setForegroundColor(MinecraftTheme.GOLD_YELLOW);
        startBtn.setEnabled(false);
        stopBtn.setEnabled(false);
        restartBtn.setEnabled(false);
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

        statusLabel.setText(GlyphHelper.apply(GlyphHelper.ICON_RESTART + " [RESTARTING...]"));
        statusLabel.setForegroundColor(MinecraftTheme.GOLD_YELLOW);
        startBtn.setEnabled(false);
        stopBtn.setEnabled(false);
        restartBtn.setEnabled(false);
        ActivityLogger.info("Restarting server...");

        new Thread(() -> {
            OrchestratorBridge.stopServer();
            try {
                Thread.sleep(1500);
            } catch (InterruptedException ignored) {}
            boolean pub = publicTunnelCheckBox.isChecked();
            String selectedRam = ramComboBox.getSelectedItem();
            OrchestratorBridge.ProcessResult res = OrchestratorBridge.startServer(pub, selectedRam);
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
