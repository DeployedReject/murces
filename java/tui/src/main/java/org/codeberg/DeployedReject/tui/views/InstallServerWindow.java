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

public class InstallServerWindow extends BasicWindow {

    private final WindowBasedTextGUI gui;
    private final ComboBox<String> engineComboBox;
    private final ComboBox<String> gameVersionComboBox;
    private final TextBox loaderVersionBox;
    private final ComboBox<String> ramComboBox;
    private final TextBox outputLog;
    private final ProgressBar progressBar;
    private final Label progressLabel;

    public InstallServerWindow(WindowBasedTextGUI gui) {
        super("Install Minecraft Server - Murces");
        this.gui = gui;
        setHints(Arrays.asList(Hint.CENTERED, Hint.FIT_TERMINAL_WINDOW));

        Panel root = new Panel(new LinearLayout(Direction.VERTICAL));
        root.setPreferredSize(new TerminalSize(74, 21));

        // Tooltip at top
        root.addComponent(KeyboardNavigationHelper.createTooltip());

        // Options form
        Panel formPanel = new Panel(new GridLayout(2));

        formPanel.addComponent(new Label("[E]ngine:"));
        engineComboBox = new ComboBox<>("Fabric", "Paper", "Spigot", "Vanilla", "Forge");
        engineComboBox.setPreferredSize(new TerminalSize(18, 1));
        formPanel.addComponent(engineComboBox);

        formPanel.addComponent(new Label("Game [V]ersion:"));
        gameVersionComboBox = MinecraftVersionHelper.createVersionComboBox(gui, new TerminalSize(18, 1));
        formPanel.addComponent(gameVersionComboBox);

        formPanel.addComponent(new Label("Loader Version:"));
        loaderVersionBox = new TextBox(new TerminalSize(18, 1), "0.16.5");
        formPanel.addComponent(loaderVersionBox);

        formPanel.addComponent(new Label("[R]AM Allocation:"));
        ramComboBox = new ComboBox<>("2G", "4G", "6G", "8G", "12G", "16G", "1G");
        ramComboBox.setPreferredSize(new TerminalSize(18, 1));
        ramComboBox.setSelectedIndex(1); // 4G default
        formPanel.addComponent(ramComboBox);

        root.addComponent(formPanel.withBorder(Borders.singleLine("Server Configuration")));

        // Action buttons
        Panel actionPanel = new Panel(new LinearLayout(Direction.HORIZONTAL));
        Button installOnlyBtn = new Button("Install Only", () -> runInstall(0));
        Button installStartBtn = new Button("Start & Install", () -> runInstall(1));
        Button cancelBtn = new Button("Cancel Install", this::cancelInstallation);
        actionPanel.addComponent(installOnlyBtn);
        actionPanel.addComponent(new EmptySpace(new TerminalSize(2, 1)));
        actionPanel.addComponent(installStartBtn);
        actionPanel.addComponent(new EmptySpace(new TerminalSize(2, 1)));
        actionPanel.addComponent(cancelBtn);
        root.addComponent(actionPanel);

        // Progress bar & Log
        progressBar = new ProgressBar(0, 100);
        progressBar.setPreferredSize(new TerminalSize(70, 1));
        progressBar.setValue(0);
        root.addComponent(progressBar);

        progressLabel = new Label("Ready to install.");
        progressLabel.setForegroundColor(MinecraftTheme.GOLD_YELLOW);
        root.addComponent(progressLabel);

        outputLog = new TextBox(new TerminalSize(70, 4));
        outputLog.setReadOnly(true);
        root.addComponent(outputLog);

        // Footer
        Panel footer = new Panel(new LinearLayout(Direction.HORIZONTAL));
        Button backBtn = new Button("Back to Main Menu", this::close);
        footer.addComponent(backBtn);
        root.addComponent(footer);

        // Hotkeys
        Map<Character, Runnable> hotkeys = new HashMap<>();
        hotkeys.put('I', KeyboardNavigationHelper.focus(installOnlyBtn, () -> runInstall(0)));
        hotkeys.put('S', KeyboardNavigationHelper.focus(installStartBtn, () -> runInstall(1)));
        hotkeys.put('E', KeyboardNavigationHelper.focus(engineComboBox, this::cycleEngine));
        hotkeys.put('V', KeyboardNavigationHelper.focus(gameVersionComboBox, () -> MinecraftVersionHelper.cycleVersion(gameVersionComboBox)));
        hotkeys.put('L', loaderVersionBox::takeFocus);
        hotkeys.put('R', KeyboardNavigationHelper.focus(ramComboBox, this::cycleRam));
        hotkeys.put('B', KeyboardNavigationHelper.focus(backBtn, this::close));
        KeyboardNavigationHelper.attach(this, hotkeys);

        setComponent(root);
    }

    private void cycleEngine() {
        int next = (engineComboBox.getSelectedIndex() + 1) % engineComboBox.getItemCount();
        engineComboBox.setSelectedIndex(next);
    }

    private void cycleRam() {
        int next = (ramComboBox.getSelectedIndex() + 1) % ramComboBox.getItemCount();
        ramComboBox.setSelectedIndex(next);
    }

    private void appendLog(String line) {
        String curr = outputLog.getText();
        if (curr.isEmpty()) {
            outputLog.setText(line);
        } else {
            outputLog.setText(curr + "\n" + line);
        }
    }

    private void cancelInstallation() {
        if (!OrchestratorBridge.isServerDownloading()) {
            appendLog("[INFO] No server installation currently running.");
            return;
        }
        appendLog("[WARN] Cancelling server installation...");
        org.codeberg.DeployedReject.tui.backend.JobTracker.getInstance().getActiveJobs().forEach(j -> {
            if ("Server".equalsIgnoreCase(j.getType())) {
                j.cancel();
            }
        });
        progressBar.setValue(0);
        progressLabel.setText("[CANCELLED] Installation cancelled.");
    }

    private void runInstall(int job) {
        if (OrchestratorBridge.isServerDownloading()) {
            MessageDialog.showMessageDialog(gui, "Busy", "A server installation is already in progress!", MessageDialogButton.OK);
            return;
        }

        String engine = engineComboBox.getSelectedItem();
        String gameVer = MinecraftVersionHelper.getSelectedVersion(gameVersionComboBox);
        String loaderVer = loaderVersionBox.getText().trim();
        String ramStr = ramComboBox.getSelectedItem().replace("G", "").trim();
        int ram = 4;
        try {
            ram = Integer.parseInt(ramStr);
        } catch (NumberFormatException ignored) {}

        if (gameVer.isEmpty()) {
            MessageDialog.showMessageDialog(gui, "Invalid Version", "Game version cannot be empty!", MessageDialogButton.OK);
            return;
        }

        progressBar.setValue(0);
        String actionName = (job == 1) ? "Install & Start" : "Install Only";
        progressLabel.setText("[BUSY] Running " + actionName + " for " + engine + " " + gameVer + " (" + ram + "G)...");
        appendLog("[INFO] Starting installation: " + engine + " " + gameVer);

        final int ramVal = ram;
        new Thread(() -> {
            try {
                OrchestratorBridge.getInstance().installServer(engine, gameVer, loaderVer, ramVal, job, msg -> {
                    if (msg != null && msg.contains("%")) {
                        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+(\\.\\d+)?)%").matcher(msg);
                        if (m.find()) {
                            try {
                                double pVal = Double.parseDouble(m.group(1));
                                int pct = (int) Math.round(pVal);
                                gui.getGUIThread().invokeLater(() -> progressBar.setValue(Math.min(100, Math.max(0, pct))));
                            } catch (Exception ignored) {}
                        }
                    }
                    gui.getGUIThread().invokeLater(() -> {
                        progressLabel.setText("[BUSY] " + msg);
                        appendLog("[STATUS] " + msg);
                    });
                }).get();

                gui.getGUIThread().invokeLater(() -> {
                    progressBar.setValue(100);
                    progressLabel.setText("[OK:] Installation completed!");
                    progressLabel.setForegroundColor(MinecraftTheme.CREEPER_GREEN);
                    appendLog("[OK:] Installation finished successfully.");
                    MessageDialog.showMessageDialog(gui, "Complete", "Server installation finished successfully!", MessageDialogButton.OK);
                });
            } catch (Exception e) {
                gui.getGUIThread().invokeLater(() -> {
                    progressLabel.setText("[ERR:] Installation failed: " + e.getMessage());
                    progressLabel.setForegroundColor(MinecraftTheme.REDSTONE_RED);
                    appendLog("[ERR:] " + e.getMessage());
                    MessageDialog.showMessageDialog(gui, "Error", "Installation failed: " + e.getMessage(), MessageDialogButton.OK);
                });
            }
        }).start();
    }
}
