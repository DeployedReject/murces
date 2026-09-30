package org.codeberg.DeployedReject.tui.views;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.*;
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
        cmdPanel.addComponent(new Label("Command: /"));
        commandInput = new TextBox(new TerminalSize(35, 1));
        cmdPanel.addComponent(commandInput);
        Button sendBtn = new Button("Dispatch Cmd", this::onSendCommand);
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
        boolean running = OrchestratorBridge.isServerRunning();
        if (running) {
            statusLabel.setText("[RUNNING] - Port 25565");
            statusLabel.setForegroundColor(MinecraftTheme.CREEPER_GREEN);
        } else {
            statusLabel.setText("[STOPPED]");
            statusLabel.setForegroundColor(MinecraftTheme.REDSTONE_RED);
        }
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
        boolean pub = publicTunnelCheckBox.isChecked();
        appendLog("[INFO] Starting server (public=" + pub + ")...");
        OrchestratorBridge.ProcessResult res = OrchestratorBridge.startServer(pub);
        if (res.exitCode == 0) {
            appendLog("[OK:] " + (res.output.isEmpty() ? "Server started successfully." : res.output));
        } else {
            appendLog("[ERR:] " + (res.output.isEmpty() ? "Failed to start server (code " + res.exitCode + ")" : res.output));
        }
        updateStatus();
    }

    private void onStop() {
        appendLog("[INFO] Stopping server...");
        OrchestratorBridge.ProcessResult res = OrchestratorBridge.stopServer();
        if (res.exitCode == 0) {
            appendLog("[OK:] " + (res.output.isEmpty() ? "Server stopped." : res.output));
        } else {
            appendLog("[ERR:] " + (res.output.isEmpty() ? "Failed to stop server." : res.output));
        }
        updateStatus();
    }

    private void onRestart() {
        onStop();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException ignored) {}
        onStart();
    }

    private void onSendCommand() {
        String cmd = commandInput.getText().trim();
        if (cmd.isEmpty()) return;
        appendLog("[INFO] Executing: /" + cmd);
        commandInput.setText("");
        OrchestratorBridge.ProcessResult res = OrchestratorBridge.sendConsoleCommand(cmd);
        if (res.output != null && !res.output.isEmpty()) {
            appendLog(res.output);
        } else {
            appendLog("[OK:] Command dispatched.");
        }
    }
}
