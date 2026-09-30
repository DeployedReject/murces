package org.codeberg.DeployedReject.tui.views;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.*;
import com.googlecode.lanterna.gui2.dialogs.MessageDialog;
import com.googlecode.lanterna.gui2.dialogs.MessageDialogButton;
import org.codeberg.DeployedReject.tui.backend.OrchestratorBridge;
import org.codeberg.DeployedReject.tui.theme.MinecraftTheme;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class ServerControlWindow extends BasicWindow {

    private final WindowBasedTextGUI gui;
    private final Label statusLabel;
    private final CheckBox publicTunnelCheckBox;
    private final TextBox commandInput;
    private final TextBox consoleLog;

    public ServerControlWindow(WindowBasedTextGUI gui) {
        super("Server Control - Murces");
        this.gui = gui;
        setHints(Arrays.asList(Hint.CENTERED, Hint.FIT_TERMINAL_WINDOW));

        Panel root = new Panel(new LinearLayout(Direction.VERTICAL));
        root.setPreferredSize(new TerminalSize(74, 21));

        // Tooltip at top
        root.addComponent(KeyboardNavigationHelper.createTooltip());

        // Header / Status
        Panel statusPanel = new Panel(new LinearLayout(Direction.HORIZONTAL));
        statusPanel.addComponent(new Label("Server Status: "));
        statusLabel = new Label("CHECKING...");
        statusPanel.addComponent(statusLabel);

        Button refreshBtn = new Button("Update Status", this::updateStatus);
        statusPanel.addComponent(new EmptySpace(new TerminalSize(2, 1)));
        statusPanel.addComponent(refreshBtn);
        root.addComponent(statusPanel.withBorder(Borders.singleLine("Status")));

        // Control Buttons
        publicTunnelCheckBox = new CheckBox("Playit Tunnel (--public)");
        root.addComponent(publicTunnelCheckBox);

        Panel actionPanel = new Panel(new LinearLayout(Direction.HORIZONTAL));
        Button startBtn = new Button("Start Server", this::onStart);
        Button stopBtn = new Button("Terminate Server", this::onStop);
        Button restartBtn = new Button("Restart Server", this::onRestart);

        actionPanel.addComponent(startBtn);
        actionPanel.addComponent(new EmptySpace(new TerminalSize(1, 1)));
        actionPanel.addComponent(stopBtn);
        actionPanel.addComponent(new EmptySpace(new TerminalSize(1, 1)));
        actionPanel.addComponent(restartBtn);
        root.addComponent(actionPanel.withBorder(Borders.singleLine("Actions")));

        // Console Command
        Panel cmdPanel = new Panel(new LinearLayout(Direction.HORIZONTAL));
        cmdPanel.addComponent(new Label("[C]onsole Command (/): "));
        commandInput = new TextBox(new TerminalSize(32, 1));
        Button sendBtn = new Button("[D]ispatch Cmd", this::onSendCommand);
        commandInput.setInputFilter((interactable, keyStroke) -> {
            if (keyStroke.getKeyType() == com.googlecode.lanterna.input.KeyType.Escape ||
                keyStroke.getKeyType() == com.googlecode.lanterna.input.KeyType.ArrowDown) {
                sendBtn.takeFocus();
                return false;
            }
            return true;
        });
        cmdPanel.addComponent(commandInput);
        cmdPanel.addComponent(new EmptySpace(new TerminalSize(1, 1)));
        cmdPanel.addComponent(sendBtn);
        root.addComponent(cmdPanel.withBorder(Borders.singleLine("Minecraft Console")));

        // Output / Log
        consoleLog = new TextBox(new TerminalSize(70, 4));
        consoleLog.setReadOnly(true);
        root.addComponent(new Label("Output Log:"));
        root.addComponent(consoleLog);

        // Footer
        Panel footer = new Panel(new LinearLayout(Direction.HORIZONTAL));
        Button backBtn = new Button("Back to Main Menu", this::close);
        footer.addComponent(backBtn);
        root.addComponent(footer);

        // Hotkeys
        Map<Character, Runnable> hotkeys = new HashMap<>();
        hotkeys.put('S', KeyboardNavigationHelper.focus(startBtn, this::onStart));
        hotkeys.put('T', KeyboardNavigationHelper.focus(stopBtn, this::onStop));
        hotkeys.put('R', KeyboardNavigationHelper.focus(restartBtn, this::onRestart));
        hotkeys.put('U', KeyboardNavigationHelper.focus(refreshBtn, this::updateStatus));
        hotkeys.put('P', KeyboardNavigationHelper.focus(publicTunnelCheckBox, () -> publicTunnelCheckBox.setChecked(!publicTunnelCheckBox.isChecked())));
        hotkeys.put('D', KeyboardNavigationHelper.focus(sendBtn, this::onSendCommand));
        hotkeys.put('B', KeyboardNavigationHelper.focus(backBtn, this::close));
        hotkeys.put('/', commandInput::takeFocus);
        hotkeys.put('C', commandInput::takeFocus);
        KeyboardNavigationHelper.attach(this, hotkeys);

        // Submit console command on Enter inside commandInput
        addWindowListener(new WindowListenerAdapter() {
            @Override
            public void onInput(Window basePane, com.googlecode.lanterna.input.KeyStroke keyStroke, java.util.concurrent.atomic.AtomicBoolean deliver) {
                if (keyStroke.getKeyType() == com.googlecode.lanterna.input.KeyType.Enter && basePane.getFocusedInteractable() == commandInput) {
                    deliver.set(false);
                    onSendCommand();
                }
            }
        });

        setComponent(root);
        updateStatus();
    }

    private void updateStatus() {
        new Thread(() -> {
            boolean running = OrchestratorBridge.isServerRunning();
            boolean installed = OrchestratorBridge.isServerInstalled();
            gui.getGUIThread().invokeLater(() -> {
                if (running) {
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

    private void appendLog(String line) {
        String current = consoleLog.getText();
        if (current.isEmpty()) {
            consoleLog.setText(line);
        } else {
            consoleLog.setText(current + "\n" + line);
        }
    }

    private void onStart() {
        if (!OrchestratorBridge.isServerInstalled()) {
            appendLog("[WARN:] No server installed! Please use '[I]nstall Server Engine' first.");
            MessageDialog.showMessageDialog(gui, "No Server Installed", "No Minecraft server is installed yet!\nPlease use '[I]nstall Server Engine' from the main menu first.", MessageDialogButton.OK);
            return;
        }
        if (OrchestratorBridge.isServerRunning()) {
            appendLog("[WARN:] Server is already running.");
            return;
        }

        boolean pub = publicTunnelCheckBox.isChecked();
        statusLabel.setText("[STARTING...]");
        statusLabel.setForegroundColor(MinecraftTheme.GOLD_YELLOW);
        appendLog("[INFO] Starting server (public=" + pub + ")...");

        new Thread(() -> {
            OrchestratorBridge.ProcessResult res = OrchestratorBridge.startServer(pub);
            gui.getGUIThread().invokeLater(() -> {
                if (res.exitCode == 0) {
                    appendLog("[OK:] " + (res.output.isEmpty() ? "Server started successfully." : res.output));
                } else {
                    appendLog("[ERR:] " + (res.output.isEmpty() ? "Failed to start server (code " + res.exitCode + ")" : res.output));
                }
                updateStatus();
            });
        }).start();
    }

    private void onStop() {
        if (!OrchestratorBridge.isServerRunning()) {
            appendLog("[WARN:] Server is not running.");
            return;
        }

        statusLabel.setText("[STOPPING...]");
        statusLabel.setForegroundColor(MinecraftTheme.GOLD_YELLOW);
        appendLog("[INFO] Stopping server...");

        new Thread(() -> {
            OrchestratorBridge.ProcessResult res = OrchestratorBridge.stopServer();
            gui.getGUIThread().invokeLater(() -> {
                if (res.exitCode == 0) {
                    appendLog("[OK:] " + (res.output.isEmpty() ? "Server stopped." : res.output));
                } else {
                    appendLog("[ERR:] " + (res.output.isEmpty() ? "Failed to stop server." : res.output));
                }
                updateStatus();
            });
        }).start();
    }

    private void onRestart() {
        if (!OrchestratorBridge.isServerInstalled()) {
            appendLog("[WARN:] No server installed! Please use '[I]nstall Server Engine' first.");
            MessageDialog.showMessageDialog(gui, "No Server Installed", "No Minecraft server is installed yet!\nPlease use '[I]nstall Server Engine' from the main menu first.", MessageDialogButton.OK);
            return;
        }

        statusLabel.setText("[RESTARTING...]");
        statusLabel.setForegroundColor(MinecraftTheme.GOLD_YELLOW);
        appendLog("[INFO] Restarting server...");

        new Thread(() -> {
            OrchestratorBridge.stopServer();
            try {
                Thread.sleep(1500);
            } catch (InterruptedException ignored) {}
            boolean pub = publicTunnelCheckBox.isChecked();
            OrchestratorBridge.ProcessResult res = OrchestratorBridge.startServer(pub);
            gui.getGUIThread().invokeLater(() -> {
                if (res.exitCode == 0) {
                    appendLog("[OK:] " + (res.output.isEmpty() ? "Server restarted." : res.output));
                } else {
                    appendLog("[ERR:] " + res.output);
                }
                updateStatus();
            });
        }).start();
    }

    private void onSendCommand() {
        String cmd = commandInput.getText().trim();
        if (cmd.isEmpty()) return;

        if (!OrchestratorBridge.isServerRunning()) {
            appendLog("[WARN:] Cannot send command: Server is not running.");
            return;
        }

        appendLog("[INFO] Executing: /" + cmd);
        commandInput.setText("");

        new Thread(() -> {
            OrchestratorBridge.ProcessResult res = OrchestratorBridge.sendConsoleCommand(cmd);
            gui.getGUIThread().invokeLater(() -> {
                if (res.output != null && !res.output.isEmpty()) {
                    appendLog(res.output);
                } else {
                    appendLog("[OK:] Command dispatched.");
                }
            });
        }).start();
    }
}
